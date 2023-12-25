package com.example.test_map.server

/**
 * Server callback
 */
interface ServerCallback {
    //Receive messages from the client
    fun receiveClientMsg(success: Boolean, msg: String)
    //Other news
    fun otherMsg(msg: String)
}