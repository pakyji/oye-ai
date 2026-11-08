package net.kuafuai.andee.net

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool schemas for the on-device brain. These are the LLM-facing tool definitions.
 * The brain reads these to know what it can do. The internal method names (used by
 * CommandDispatcher) are different — see [methodOf] for translation.
 *
 * The description strings are prompt text read by an LLM — they are behavioral
 * documentation, not code comments, and are meant to stay in sync with the
 * equivalent dispatcher branches.
 */
object ToolSchemas {

    /** All tool schemas as a JSON array for the model. */
    val all: JSONArray = JSONArray().apply {
        put(screenTools)
        put(uiTools)
        put(deviceTools)
        put(notebookTools)
        put(todoTools)
        put(vaultTools)
        put(peerTools)
        put(sceneTools)
        put(dogTools)
        put(meetingTools)
        put(topicTools)
        put(gearTools)
        put(systemTools)
    }

    // ============ screen.* ============

    private val screenTools = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "get_screen_element")
            put("description", "Read the current screen as an element outline. Returns one line per element with eid, clickable marker, class, label, and center in 0-1000 relative coordinates. Use this first to find what to tap. Set verbose=true for full nested JSON (huge).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("verbose", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "Return full nested JSON instead of outline (huge, rarely needed)")
                    })
                })
            })
        })
    }

    private val screenTap = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "tap_screen_element")
            put("description", "Tap an element by its eid from get_screen_element. The device looks up the pixel center and taps it. Stale/unknown eids fail loudly — re-dump, don't guess.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("eid"))
                put("properties", JSONObject().apply {
                    put("eid", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Element ID from get_screen_element")
                    })
                })
            })
        })
    }

    private val screenLongPress = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "long_press_screen_element")
            put("description", "Long-press an element by eid. Same eid rules as tap_screen_element.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("eid"))
                put("properties", JSONObject().apply {
                    put("eid", JSONObject().apply {
                        put("type", "integer")
                    })
                })
            })
        })
    }

    private val screenTapCoords = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "tap_by_coordinates")
            put("description", "Tap by 0-1000 relative coordinates (last resort for self-drawn panes). Values outside 0-1000 are rejected, never clamped. Takes cell + part from grid for precision.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("x").put("y"))
                put("properties", JSONObject().apply {
                    put("x", JSONObject().apply { put("type", "integer").put("description", "0-1000 relative X") })
                    put("y", JSONObject().apply { put("type", "integer").put("description", "0-1000 relative Y") })
                    put("cell", JSONObject().apply { put("type", "string").put("description", "Grid cell like D7 (for debugging)") })
                    put("part", JSONObject().apply { put("type", "string").put("description", "Part like bottom-right (for debugging)") })
                    put("on", JSONObject().apply { put("type", "string").put("description", "Set to 'zoom' if this tap follows a zoom_screen_region") })
                })
            })
        })
    }

    private val screenLongPressCoords = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "long_press_by_coordinates")
            put("description", "Long-press by 0-1000 relative coordinates. Same rules as tap_by_coordinates.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("x").put("y"))
                put("properties", JSONObject().apply {
                    put("x", JSONObject().apply { put("type", "integer") })
                    put("y", JSONObject().apply { put("type", "integer") })
                    put("cell", JSONObject().apply { put("type", "string") })
                    put("part", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val screenSwipe = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "swipe_by_coordinates")
            put("description", "Swipe by 0-1000 relative coordinates. Does not coast by default — holds at endpoint to prevent fling overshoot. Use fling=true for long jumps (then re-look).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("x1").put("y1").put("x2").put("y2"))
                put("properties", JSONObject().apply {
                    put("x1", JSONObject().apply { put("type", "integer") })
                    put("y1", JSONObject().apply { put("type", "integer") })
                    put("x2", JSONObject().apply { put("type", "integer") })
                    put("y2", JSONObject().apply { put("type", "integer") })
                    put("fling", JSONObject().apply { put("type", "boolean").put("description", "Coast like a fling (default false — holds at end)") })
                    put("cell1", JSONObject().apply { put("type", "string") })
                    put("part1", JSONObject().apply { put("type", "string") })
                    put("cell2", JSONObject().apply { put("type", "string") })
                    put("part2", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val screenType = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "type_text")
            put("description", "Type text into the focused field. Uses the accessibility IME channel (API 33+) or ADBKeyboard fallback. Returns performed + via + len. On failure, returns error with readback fragment. After typing on a tree-blind app, only a tap with on='zoom' is accepted.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("text"))
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply { put("type", "string") })
                    put("submit", JSONObject().apply { put("type", "boolean").put("description", "Also press Enter/Send after typing") })
                })
            })
        })
    }

    private val screenSubmit = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "submit_input")
            put("description", "Press Enter/Send on the focused field. Uses the field's own EditorInfo to pick the right action (Send, Search, Next).")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val screenGlobal = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "global_action")
            put("description", "Perform a global action: back, home, recent_apps, notifications, quick_settings, power_dialog.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("action"))
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("back").put("home").put("recent_apps").put("notifications").put("quick_settings").put("power_dialog")) })
                })
            })
        })
    }

    private val screenLaunch = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "launch_app")
            put("description", "Launch an app by package name. Use for system settings (com.android.settings) or any known package.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("package_name"))
                put("properties", JSONObject().apply {
                    put("package_name", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val screenWait = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "wait_for_screen")
            put("description", "Wait for the screen to change. Seconds must grow each call: 3→5→10→20. Never send the same number twice. Total wait budget ~90s. Returns screen_changed + screenshot + note.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("seconds"))
                put("properties", JSONObject().apply {
                    put("seconds", JSONObject().apply { put("type", "integer").put("minimum", 1).put("maximum", 20) })
                })
            })
        })
    }

    private val screenZoom = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "zoom_screen_region")
            put("description", "Magnify a grid cell + part. Returns a close-up PNG with a magenta crosshair exactly where a tap would land. Use before tap_by_coordinates on blind apps. Returns region (the actual rectangle shown) so you can verify coordinate space.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("cell").put("part"))
                put("properties", JSONObject().apply {
                    put("cell", JSONObject().apply { put("type", "string").put("description", "Grid cell like D7") })
                    put("part", JSONObject().apply { put("type", "string").put("description", "One of: top-left, top, top-right, upper-left, upper, upper-right, center-left, center, center-right, lower-left, lower, lower-right, bottom-left, bottom, bottom-right") })
                })
            })
        })
    }

    private val screenScreenshot = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "take_screenshot")
            put("description", "Take a full-screen screenshot (downscaled to 1280 long side). Returns base64 PNG. Use when you need to see the whole screen at once.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val screenTapTarget = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "tap_target")
            put("description", "Describe the one element you want to tap in words (its text/icon and where it is), and the device finds and taps it using a vision model. Far more precise than aiming by eye. Fallback, not a gate — if unavailable, the tool disappears and you aim by grid. The located point counts as aimed (passes blind-app zoom rule).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("description"))
                put("properties", JSONObject().apply {
                    put("description", JSONObject().apply { put("type", "string").put("description", "Describe the target: text, icon, position. e.g., 'red delete button at top right of the card'") })
                })
            })
        })
    }

    private val screenPopup = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "get_popups")
            put("description", "List open popups from the window list. Returns popup_open boolean and popups array with bounds. Works even when the app withholds its accessibility tree.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    // ============ ui.* ============

    private val uiTools = JSONArray().apply {
        put(uiAsk)
        put(uiAlert)
        put(uiShowHtml)
        put(uiListPages)
        put(uiGetPage)
    }

    private val uiAsk = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "ui.ask")
            put("description", "Ask the user a question with buttons. Blocks until they answer or timeout. Returns the button text they chose. Use for confirmations, choices, or when you need their input to proceed.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("question").put("buttons"))
                put("properties", JSONObject().apply {
                    put("question", JSONObject().apply { put("type", "string") })
                    put("buttons", JSONObject().apply { put("type", "array").put("items", JSONObject().apply { put("type", "string") }) })
                })
            })
        })
    }

    private val uiAlert = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "ui.alert")
            put("description", "Show a message to the user with one OK button. Does not block — returns immediately. Use for FYI messages.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("message"))
                put("properties", JSONObject().apply {
                    put("message", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val uiShowHtml = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "ui.show_html")
            put("description", "Show an HTML page to the user. Returns immediately with closed=false. The page stays up until the user dismisses it. Use for showing results, forms, articles, or anything that needs a full screen. Optional revise parameter to replace an existing page by group id.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("html"))
                put("properties", JSONObject().apply {
                    put("html", JSONObject().apply { put("type", "string") })
                    put("title", JSONObject().apply { put("type", "string") })
                    put("revise", JSONObject().apply { put("type", "string").put("description", "Group ID of page to replace") })
                })
            })
        })
    }

    private val uiListPages = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "ui.list_pages")
            put("description", "List all generated pages (artifacts), one per group with current version and version count. Read-only, localOnly.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val uiGetPage = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "ui.get_page")
            put("description", "Read a page by its group id and optional version. Returns the HTML source. Any historical version can be read. Read-only, localOnly.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("group_id"))
                put("properties", JSONObject().apply {
                    put("group_id", JSONObject().apply { put("type", "string") })
                    put("version", JSONObject().apply { put("type", "integer").put("description", "Version number (default: latest)") })
                })
            })
        })
    }

    // ============ device.* ============

    private val deviceTools = JSONArray().apply {
        put(deviceCall)
        put(deviceSms)
        put(deviceContacts)
        put(deviceCalendar)
        put(deviceLocation)
        put(deviceCamera)
        put(deviceNotification)
        put(deviceOpenUrl)
        put(deviceClipboard)
        put(deviceRecordAudio)
        put(devicePlayAudio)
        put(deviceVibrate)
    }

    private val deviceCall = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.call")
            put("description", "Call a contact by name or number. Returns performed + via (direct/dialer). Needs CALL_PHONE permission for direct call.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("target"))
                put("properties", JSONObject().apply {
                    put("target", JSONObject().apply { put("type", "string").put("description", "Contact name or phone number with country code") })
                })
            })
        })
    }

    private val deviceSms = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.sms")
            put("description", "Send an SMS. Opens the SMS app with the message ready — user taps Send. Returns performed.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("target").put("message"))
                put("properties", JSONObject().apply {
                    put("target", JSONObject().apply { put("type", "string") })
                    put("message", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val deviceContacts = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.contacts")
            put("description", "Search contacts by name. Returns list of matches with phone numbers. Needs READ_CONTACTS.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("query"))
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val deviceCalendar = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.calendar")
            put("description", "Read or write calendar events. Action: read | write. For write, provide title, start, end, description. Returns event list or created event.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("action"))
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("read").put("write")) })
                    put("title", JSONObject().apply { put("type", "string") })
                    put("start", JSONObject().apply { put("type", "string") })
                    put("end", JSONObject().apply { put("type", "string") })
                    put("description", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val deviceLocation = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.location")
            put("description", "Get current location. Returns lat/lng/accuracy/timestamp. Needs location permission.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val deviceCamera = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.camera")
            put("description", "Take a photo with the camera. Camera: front | back. Returns base64 JPEG. The shutter flashes the corners as a privacy tell.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("camera", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("front").put("back")) })
                })
            })
        })
    }

    private val deviceNotification = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.notifications")
            put("description", "Read recent notifications. Returns list with app, title, text, time, chat flag. Use to catch up on what arrived.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val deviceOpenUrl = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.open_url")
            put("description", "Open a URL in the browser. Returns performed.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("url"))
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val deviceClipboard = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.clipboard")
            put("description", "Read or write clipboard. Action: read | write. For write, provide text.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("action"))
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("read").put("write")) })
                    put("text", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val deviceRecordAudio = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.record_audio")
            put("description", "Record audio for a duration. Returns base64 WAV. Use for voice memos or meeting recording.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("duration_seconds"))
                put("properties", JSONObject().apply {
                    put("duration_seconds", JSONObject().apply { put("type", "integer") })
                })
            })
        })
    }

    private val devicePlayAudio = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.play_audio")
            put("description", "Play a base64 WAV or MP3. Returns performed.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("audio_base64"))
                put("properties", JSONObject().apply {
                    put("audio_base64", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val deviceVibrate = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "device.vibrate")
            put("description", "Vibrate the device. Pattern: array of ms (on/off/on/off...). Returns performed.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("pattern"))
                put("properties", JSONObject().apply {
                    put("pattern", JSONObject().apply { put("type", "array").put("items", JSONObject().apply { put("type", "integer") }) })
                })
            })
        })
    }

    // ============ notebook.* ============

    private val notebookTools = JSONArray().apply {
        put(noteRecall)
        put(noteRemember)
        put(noteForget)
        put(noteSelf)
        put(noteCares)
        put(noteDiary)
        put(notePeople)
        put(noteWikiWrite)
        put(noteWikiRead)
        put(noteWikiList)
        put(noteWikiShow)
        put(noteGuard)
        put(noteNotifyRule)
    }

    private val noteRecall = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.recall")
            put("description", "Search memories by query. Returns matching key-value pairs. Run this before you start anything — have you handled this before?")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("query"))
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val noteRemember = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.remember")
            put("description", "Write a memory. Key = name (updates if exists), value = what to remember. Write on the spot — next time I deal with them, would not knowing this make me get it wrong? Yes → write it down.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("key").put("value"))
                put("properties", JSONObject().apply {
                    put("key", JSONObject().apply { put("type", "string") })
                    put("value", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val noteForget = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.forget")
            put("description", "Delete a memory by key. Recall first to check the name — a deletion cannot be undone.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("key"))
                put("properties", JSONObject().apply {
                    put("key", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val noteSelf = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.self")
            put("description", "Update your self-description. Free text. Only on trusted turns (not NOTIFY/PEER).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("text"))
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val noteCares = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.cares")
            put("description", "Manage your cares (long-term directions). Action: add | update | drop | pause | resume. Each has name, description, interval_days, source.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("action"))
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("add").put("update").put("drop").put("pause").put("resume")) })
                    put("name", JSONObject().apply { put("type", "string") })
                    put("description", JSONObject().apply { put("type", "string") })
                    put("interval_days", JSONObject().apply { put("type", "integer") })
                    put("source", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val noteDiary = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.diary")
            put("description", "Read diary entries. Date: yyyy-MM-DD (default: today). Only on trusted turns.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("date", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val notePeople = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.people")
            put("description", "Manage people you've met. Action: add | update | read. Name + notes.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("action"))
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("add").put("update").put("read")) })
                    put("name", JSONObject().apply { put("type", "string") })
                    put("notes", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val noteWikiWrite = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.wiki_write")
            put("description", "Write a wiki article. Title + body (markdown). Optional secret=true. Only on trusted turns. If show=true, opens the article in the reader.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("title").put("body"))
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply { put("type", "string") })
                    put("body", JSONObject().apply { put("type", "string") })
                    put("secret", JSONObject().apply { put("type", "boolean") })
                    put("show", JSONObject().apply { put("type", "boolean") })
                })
            })
        })
    }

    private val noteWikiRead = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.wiki_read")
            put("description", "Read a wiki article by filename. Returns title + body.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("filename"))
                put("properties", JSONObject().apply {
                    put("filename", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val noteWikiList = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.wiki_list")
            put("description", "List wiki articles. Returns title, filename, secret, updated_at.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val noteWikiShow = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.wiki_show")
            put("description", "Open a wiki article in the device's reader. Filename required.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("filename"))
                put("properties", JSONObject().apply {
                    put("filename", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val noteGuard = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.guard")
            put("description", "Read the user's guard paragraph. Returns the text (may be empty).")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val noteNotifyRule = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "note.notify_rule")
            put("description", "Set or read the owner's notification rule. Action: set | read. Text ≤600 chars. Only on user turns (not silent/untrusted).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("action"))
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("set").put("read")) })
                    put("text", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    // ============ todo.* ============

    private val todoTools = JSONArray().apply {
        put(todoSet)
        put(todoList)
        put(todoDone)
        put(todoPause)
        put(todoDrop)
    }

    private val todoSet = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "todo.set")
            put("description", "Set a todo (promise). One-off with at (ISO timestamp) or recurring with cron. Must see success result before saying 'it's set'. Returns scheduled (exact/inexact) + id. Recurring min 15 min.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("text"))
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply { put("type", "string") })
                    put("at", JSONObject().apply { put("type", "string").put("description", "ISO timestamp for one-off") })
                    put("cron", JSONObject().apply { put("type", "string").put("description", "Cron expression for recurring") })
                })
            })
        })
    }

    private val todoList = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "todo.list")
            put("description", "List active todos. Returns id, text, at, cron, recurring, next_run.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val todoDone = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "todo.done")
            put("description", "Mark a todo done by id. Required even when it failed — say honestly why. Do not close recurring todos.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("id"))
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "string") })
                    put("note", JSONObject().apply { put("type", "string").put("description", "Why done or what happened") })
                })
            })
        })
    }

    private val todoPause = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "todo.pause")
            put("description", "Pause a todo by id. Only when the user asks.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("id"))
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val todoDrop = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "todo.drop")
            put("description", "Drop (cancel) a todo by id. Only when the user asks.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("id"))
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    // ============ vault.* ============

    private val vaultTools = JSONArray().apply {
        put(vaultList)
        put(vaultGet)
        put(vaultFill)
    }

    private val vaultList = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "vault.list")
            put("description", "List vault entries (masked overview). Returns label + phone/email/username/note. Passwords never listed.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val vaultGet = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "vault.get")
            put("description", "Read ONE readable field of an entry by label. Field: phone | email | username | note. Refuses field=password outright — a masked value would get pasted into a form.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("label").put("field"))
                put("properties", JSONObject().apply {
                    put("label", JSONObject().apply { put("type", "string") })
                    put("field", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("phone").put("email").put("username").put("note")) })
                })
            })
        })
    }

    private val vaultFill = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "vault.fill")
            put("description", "Type the password of an entry into the focused field. The device pulses and logs 'Filling password for X'. No echo/readback of content (secret=true). Returns performed + via + len.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("label"))
                put("properties", JSONObject().apply {
                    put("label", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    // ============ peer.* ============

    private val peerTools = JSONArray().apply {
        put(peerAsk)
        put(peerList)
    }

    private val peerAsk = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "peer.ask")
            put("description", "Ask a paired friend (another Andee device) for help. Friend: device name. Task: what you want them to do. Returns when the request is taken; the report arrives later as its own turn. Refused on untrusted turns.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("friend").put("task"))
                put("properties", JSONObject().apply {
                    put("friend", JSONObject().apply { put("type", "string") })
                    put("task", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val peerList = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "peer.list")
            put("description", "List paired friends. Returns name, trust (OWN/FRIEND), last_seen, address.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    // ============ scene.* ============

    private val sceneTools = JSONArray().apply {
        put(sceneSave)
        put(sceneEnter)
        put(sceneExit)
        put(sceneDelete)
        put(sceneList)
    }

    private val sceneSave = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "scene.save")
            put("description", "Save a scene (goal + voice + rules + routine + example). Learned, not shipped. Only on trusted turns.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("name").put("goal").put("voice").put("rules").put("routine").put("example"))
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "string") })
                    put("goal", JSONObject().apply { put("type", "string") })
                    put("voice", JSONObject().apply { put("type", "string") })
                    put("rules", JSONObject().apply { put("type", "string") })
                    put("routine", JSONObject().apply { put("type", "string") })
                    put("example", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val sceneEnter = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "scene.enter")
            put("description", "Enter a scene by name. Becomes §13 of system prompt. Triage reads it. Refused on untrusted turns.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("name"))
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val sceneExit = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "scene.exit")
            put("description", "Exit the current scene. Always allowed (leaving only narrows).")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val sceneDelete = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "scene.delete")
            put("description", "Delete a scene by name. Only on trusted turns.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("name"))
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val sceneList = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "scene.list")
            put("description", "List saved scenes. Returns name, goal, example.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    // ============ dog.* ============

    private val dogTools = JSONArray().apply {
        put(dogMove)
        put(dogStop)
        put(dogSequence)
        put(dogTurn)
        put(dogPose)
        put(dogSpeed)
        put(dogSense)
        put(dogStatus)
    }

    private val dogMove = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "dog.move")
            put("description", "Move the robot dog. Direction: forward, backward, left, right, spin_left, spin_right. Returns immediately; a scheduled stop ends it. A second move replaces the first.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("direction"))
                put("properties", JSONObject().apply {
                    put("direction", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("forward").put("backward").put("left").put("right").put("spin_left").put("spin_right")) })
                })
            })
        })
    }

    private val dogStop = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "dog.stop")
            put("description", "Stop the robot dog immediately. Half the protocol — the dog holds its last command until told otherwise.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val dogSequence = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "dog.sequence")
            put("description", "Run a multi-step motion routine. Steps: array of {direction, duration_ms}. Returns immediately; the routine runs on the dongle.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("steps"))
                put("properties", JSONObject().apply {
                    put("steps", JSONObject().apply {
                        put("type", "array")
                        put("items", JSONObject().apply {
                            put("type", "object")
                            put("required", JSONArray().put("direction").put("duration_ms"))
                            put("properties", JSONObject().apply {
                                put("direction", JSONObject().apply { put("type", "string") })
                                put("duration_ms", JSONObject().apply { put("type", "integer") })
                            })
                        })
                    })
                })
            })
        })
    }

    private val dogTurn = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "dog.turn")
            put("description", "Turn the dog to a relative angle (degrees). Closed-loop: drives until gyroscope reports the angle. Returns immediately; result lands in dog_status.last_turn after coast.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("degrees"))
                put("properties", JSONObject().apply {
                    put("degrees", JSONObject().apply { put("type", "number") })
                })
            })
        })
    }

    private val dogPose = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "dog.pose")
            put("description", "Set dog stance: stand, sit, lie. Takes effect from next motion. Returns immediately.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("pose"))
                put("properties", JSONObject().apply {
                    put("pose", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("stand").put("sit").put("lie")) })
                })
            })
        })
    }

    private val dogSpeed = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "dog.speed")
            put("description", "Set dog speed: slow, normal, fast. Takes effect from next motion. Returns immediately.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("speed"))
                put("properties", JSONObject().apply {
                    put("speed", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("slow").put("normal").put("fast")) })
                })
            })
        })
    }

    private val dogSense = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "dog.sense")
            put("description", "Request fresh telemetry from the dog (distance, battery). Returns immediately with latest reading + age_ms.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val dogStatus = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "dog.status")
            put("description", "Get dog link status. Returns open, mode (dpl/legacy), telemetry_supported, last_turn.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    // ============ meeting.* ============

    private val meetingTools = JSONArray().apply {
        put(meetingStart)
        put(meetingStop)
        put(meetingStatus)
    }

    private val meetingStart = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "meeting.start")
            put("description", "Start recording a meeting. Holds the mic, rotates 2-min segments, appends transcript to file. The ball must stay reachable — triple-tap stops. Returns started.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val meetingStop = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "meeting.stop")
            put("description", "Stop the meeting recording. Returns stopped + transcript_path. The device asks for minutes on asr.final.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val meetingStatus = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "meeting.status")
            put("description", "Get meeting recorder status. Returns active, duration_seconds, transcript_path.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    // ============ topic.* ============

    private val topicTools = JSONArray().apply {
        put(topicFold)
        put(topicList)
        put(topicGet)
    }

    private val topicFold = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "topic.fold")
            put("description", "Fold a topic (review-only). from_turn and to_turn are user-turn numbers from the review's numbered list. Max 3 folds/review, max 60 topics global. Refused on non-review turns.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("from_turn").put("to_turn").put("title").put("summary").put("text"))
                put("properties", JSONObject().apply {
                    put("from_turn", JSONObject().apply { put("type", "integer") })
                    put("to_turn", JSONObject().apply { put("type", "integer") })
                    put("title", JSONObject().apply { put("type", "string") })
                    put("summary", JSONObject().apply { put("type", "string") })
                    put("text", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val topicList = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "topic.list")
            put("description", "List folded topics. Returns id, title, summary, from_turn, to_turn, turn_count. Read-only, localOnly.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val topicGet = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "topic.get")
            put("description", "Read a folded topic by id. Returns full text (truncated at 40k). Read-only, localOnly.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("id"))
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    // ============ gear.* ============

    private val gearTools = JSONArray().apply {
        put(gearList)
        put(gearNote)
        put(gearVisibility)
    }

    private val gearList = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "gear.list")
            put("description", "List installed tools/agents with notes. Returns package, name, description, capabilities, limit, signed_in_at, visibility.")
            put("parameters", JSONObject().apply { put("type", "object") })
        })
    }

    private val gearNote = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "gear.note")
            put("description", "Write a note about an installed tool. Package + what it does + what it can do on user's behalf + limit + whether you saw it signed in.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("package_name").put("note"))
                put("properties", JSONObject().apply {
                    put("package_name", JSONObject().apply { put("type", "string") })
                    put("note", JSONObject().apply { put("type", "string") })
                })
            })
        })
    }

    private val gearVisibility = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "gear.visibility")
            put("description", "Change visibility of a tool category. Category: adult, finance, health, dating, all. Visibility: hidden, shown, default. Only when user asks.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("category").put("visibility"))
                put("properties", JSONObject().apply {
                    put("category", JSONObject().apply { put("type", "string") })
                    put("visibility", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("hidden").put("shown").put("default")) })
                })
            })
        })
    }

    // ============ system.* ============

    private val systemTools = JSONArray().apply {
        put(systemAction)
    }

    private val systemAction = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "system.action")
            put("description", "System actions: request_permissions (mode: solo/handoff/settings), open_settings, reboot (needs root).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().put("action"))
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("request_permissions").put("open_settings").put("reboot")) })
                    put("mode", JSONObject().apply { put("type", "string").put("enum", JSONArray().put("solo").put("handoff").put("settings")) })
                })
            })
        })
    }

    /** Translate LLM-facing tool name to internal dispatcher method name. */
    fun methodOf(toolName: String): String = when (toolName) {
        "get_screen_element" -> "screen.ui_tree"
        "tap_screen_element" -> "screen.tap_id"
        "long_press_screen_element" -> "screen.long_press_id"
        "tap_by_coordinates" -> "screen.tap"
        "long_press_by_coordinates" -> "screen.long_press"
        "swipe_by_coordinates" -> "screen.swipe"
        "type_text" -> "screen.type"
        "submit_input" -> "screen.submit"
        "global_action" -> "screen.global"
        "launch_app" -> "device.launch"
        "wait_for_screen" -> "screen.wait"
        "zoom_screen_region" -> "screen.zoom"
        "take_screenshot" -> "screen.screenshot"
        "tap_target" -> "screen.tap_target"
        "get_popups" -> "screen.popups"
        "ui.ask" -> "ui.ask"
        "ui.alert" -> "ui.alert"
        "ui.show_html" -> "ui.show_html"
        "ui.list_pages" -> "ui.list_pages"
        "ui.get_page" -> "ui.get_page"
        "device.call" -> "device.call"
        "device.sms" -> "device.sms"
        "device.contacts" -> "device.contacts"
        "device.calendar" -> "device.calendar"
        "device.location" -> "device.location"
        "device.camera" -> "device.camera"
        "device.notifications" -> "device.notifications"
        "device.open_url" -> "device.open_url"
        "device.clipboard" -> "device.clipboard"
        "device.record_audio" -> "device.record_audio"
        "device.play_audio" -> "device.play_audio"
        "device.vibrate" -> "device.vibrate"
        "note.recall" -> "note.recall"
        "note.remember" -> "note.remember"
        "note.forget" -> "note.forget"
        "note.self" -> "note.self"
        "note.cares" -> "note.cares"
        "note.diary" -> "note.diary"
        "note.people" -> "note.people"
        "note.wiki_write" -> "note.wiki_write"
        "note.wiki_read" -> "note.wiki_read"
        "note.wiki_list" -> "note.wiki_list"
        "note.wiki_show" -> "note.wiki_show"
        "note.guard" -> "note.guard"
        "note.notify_rule" -> "note.notify_rule"
        "todo.set" -> "todo.set"
        "todo.list" -> "todo.list"
        "todo.done" -> "todo.done"
        "todo.pause" -> "todo.pause"
        "todo.drop" -> "todo.drop"
        "vault.list" -> "vault.list"
        "vault.get" -> "vault.get"
        "vault.fill" -> "vault.fill"
        "peer.ask" -> "peer.ask"
        "peer.list" -> "peer.list"
        "scene.save" -> "scene.save"
        "scene.enter" -> "scene.enter"
        "scene.exit" -> "scene.exit"
        "scene.delete" -> "scene.delete"
        "scene.list" -> "scene.list"
        "dog.move" -> "dog.move"
        "dog.stop" -> "dog.stop"
        "dog.sequence" -> "dog.sequence"
        "dog.turn" -> "dog.turn"
        "dog.pose" -> "dog.pose"
        "dog.speed" -> "dog.speed"
        "dog.sense" -> "dog.sense"
        "dog.status" -> "dog.status"
        "meeting.start" -> "meeting.start"
        "meeting.stop" -> "meeting.stop"
        "meeting.status" -> "meeting.status"
        "topic.fold" -> "topic.fold"
        "topic.list" -> "topic.list"
        "topic.get" -> "topic.get"
        "gear.list" -> "gear.list"
        "gear.note" -> "gear.note"
        "gear.visibility" -> "gear.visibility"
        "system.action" -> "system.action"
        else -> toolName
    }
}