package org.resistentialism.sealed

import org.json.JSONObject

class Message(internal var json: JSONObject) {

    val body: String
        get() = this.json.getString("message_body")

    val name: String
        get() = this.json.getString("message_name")
}
