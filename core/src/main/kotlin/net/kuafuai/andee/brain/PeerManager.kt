package net.kuafuai.andee.brain

import net.kuafuai.andee.config.Notebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement

/**
 * Manages QR-paired friends (peers) with ECDH key exchange.
 * Each peer has a shared secret derived from ECDH for encrypted messaging.
 */
class PeerManager(private val notebook: Notebook) {

    data class Peer(
        val id: String,           // Base64 encoded public key (short)
        val name: String,
        val publicKey: String,    // Base64 encoded X.509 public key
        val sharedSecret: String, // Base64 encoded derived secret
        val trusted: Boolean = true,
        val pairedAt: Long = System.currentTimeMillis(),
        val lastSeenAt: Long = 0
    )

    private val peerKey = "peers"

    /** All peers */
    val peers: List<Peer>
        get() = getPeersFromNotebook()

    /** Our key pair for ECDH */
    val myKeyPair: KeyPair by lazy { generateKeyPair() }

    /** Our public key (Base64) */
    val myPublicKeyB64: String = Base64.getEncoder().encodeToString(myKeyPair.public.encoded)

    /** Our peer ID (short hash of public key) */
    val myPeerId: String = myPublicKeyB64.take(12)

    private fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256) // secp256r1
        return kpg.generateKeyPair()
    }

    /** Derive shared secret from peer's public key */
    private fun deriveSharedSecret(peerPublicKeyB64: String): String {
        val peerPublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(peerPublicKeyB64)))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myKeyPair.private)
        ka.doPhase(peerPublicKey, true)
        return Base64.getEncoder().encodeToString(ka.generateSecret())
    }

    /** Get peers from notebook storage */
    private fun getPeersFromNotebook(): List<Peer> {
        val json = notebook.data.memories[peerKey] ?: "[]"
        return com.google.gson.Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<Peer>>() {}.type)
    }

    /** Save peers to notebook */
    private suspend fun savePeers(peers: List<Peer>) = withContext(Dispatchers.IO) {
        val json = com.google.gson.Gson().toJson(peers)
        val current = notebook.data
        val updatedMemories = current.memories.toMutableMap().apply { put(peerKey, json) }
        notebook.data = current.copy(memories = updatedMemories)
    }

    /** Invite a peer (generate QR code data) */
    fun inviteData(): String = com.google.gson.Gson().toJson(mapOf(
        "type" to "andee_peer_invite",
        "peer_id" to myPeerId,
        "public_key" to myPublicKeyB64,
        "name" to "Device" // TODO: get from settings
    ))

    /** Accept invite and pair */
    suspend fun acceptInvite(inviteJson: String): Peer? = withContext(Dispatchers.IO) {
        try {
            val invite = com.google.gson.Gson().fromJson(inviteJson, Map::class.java)
            val peerId = invite["peer_id"] as String
            val peerPublicKey = invite["public_key"] as String
            val peerName = invite["name"] as String? ?: "Peer"

            // Check if already paired
            if (peers.any { it.id == peerId }) return@withContext null

            val sharedSecret = deriveSharedSecret(peerPublicKey)
            val peer = Peer(
                id = peerId,
                name = peerName,
                publicKey = peerPublicKey,
                sharedSecret = sharedSecret,
                trusted = true
            )

            val updatedPeers = peers.toMutableList().apply { add(peer) }
            savePeers(updatedPeers)
            peer
        } catch (e: Exception) {
            null
        }
    }

    /** Send encrypted message to peer */
    suspend fun sendMessage(peerId: String, plaintext: String): Boolean = withContext(Dispatchers.IO) {
        val peer = peers.find { it.id == peerId } ?: return@withContext false
        // Encrypt with shared secret (AES-GCM)
        val encrypted = encryptWithSharedSecret(peer.sharedSecret, plaintext)
        // TODO: Actually send via WebSocket/push
        // For now, just update lastSeen
        updatePeerLastSeen(peerId)
        true
    }

    /** Decrypt message from peer */
    fun decryptMessage(peerId: String, ciphertext: String): String? {
        val peer = peers.find { it.id == peerId } ?: return null
        return decryptWithSharedSecret(peer.sharedSecret, ciphertext)
    }

    /** Update peer last seen */
    private suspend fun updatePeerLastSeen(peerId: String) = withContext(Dispatchers.IO) {
        val updatedPeers = peers.map { p ->
            if (p.id == peerId) p.copy(lastSeenAt = System.currentTimeMillis()) else p
        }
        savePeers(updatedPeers)
    }

    /** Remove peer */
    suspend fun removePeer(peerId: String) = withContext(Dispatchers.IO) {
        val updatedPeers = peers.filter { it.id != peerId }
        savePeers(updatedPeers)
    }

    /** Peer index for prompt */
    fun friendsIndex(): String {
        if (peers.isEmpty()) return ""
        val sb = StringBuilder("FRIENDS (paired peers):\n")
        for (p in peers) {
            val trusted = if (p.trusted) " [trusted]" else " [untrusted]"
            sb.append("  - ${p.name} (${p.id})$trusted\n")
        }
        return sb.toString().trim()
    }

    // Simple AES-GCM encryption with shared secret
    private fun encryptWithSharedSecret(secretB64: String, plaintext: String): String {
        val secret = Base64.getDecoder().decode(secretB64)
        val key = javax.crypto.spec.SecretKeySpec(secret, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val iv = java.security.SecureRandom().apply { nextBytes(ByteArray(12)) }.let { ByteArray(12).also { nextBytes(it) } }
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    private fun decryptWithSharedSecret(secretB64: String, ciphertextB64: String): String? {
        try {
            val secret = Base64.getDecoder().decode(secretB64)
            val key = javax.crypto.spec.SecretKeySpec(secret, "AES")
            val combined = Base64.getDecoder().decode(ciphertextB64)
            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext)
        } catch (e: Exception) {
            return null
        }
    }
}