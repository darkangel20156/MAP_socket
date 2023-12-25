package com.example.test_map

import android.net.wifi.WifiManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test_map.client.ClientCallback
import com.example.test_map.client.SocketClient
import com.example.test_map.databinding.ActivityMainBinding
import com.example.test_map.server.ServerCallback
import com.example.test_map.server.SocketServer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity(), ServerCallback, ClientCallback {

    private val TAG = MainActivity::class.java.simpleName

    private lateinit var binding: ActivityMainBinding

    private val buffer = StringBuffer()

    //Whether it is currently the server
    private var isServer = true

    //Whether the Socket service is open
    private var openSocket = false

    //Is the Socket service connected?
    private var connectSocket = false

    //Message list
    private val messages = ArrayList<Message>()
    //Message adapter
    private lateinit var msgAdapter: MsgAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initView()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initView() {
        binding.tvIpAddress.text = "Ip address: ${getIp()}"
        //Switch between server and client
        binding.rg.setOnCheckedChangeListener { _, checkedId ->
            isServer = when (checkedId) {
                R.id.rb_server -> true
                R.id.rb_client -> false
                else -> true
            }
            binding.layServer.visibility = if (isServer) View.VISIBLE else View.GONE
            binding.layClient.visibility = if (isServer) View.GONE else View.VISIBLE
            binding.etMsg.hint = if (isServer) "Send to client" else "Send to server"
        }
        //Open service/Close service Server-side processing
        binding.btnStartService.setOnClickListener {
            openSocket = if (openSocket) {
                SocketServer.stopServer();false
            } else SocketServer.startServer(this)
            //display log
            showMsg(if (openSocket) "Open service" else "Close service")
            //Change button text
            binding.btnStartService.text = if (openSocket) "Close the service" else "Open the service"
        }
        //Connect service/disconnect client processing
//        binding.btnConnectService.setOnClickListener {
//            val ip = binding.etIpAddress.text.toString()
//            if (ip.isEmpty()) {
//                showMsg("Please enter the IP address");return@setOnClickListener
//            }
//            connectSocket = if (connectSocket) {
//                SocketClient.closeConnect();false
//            } else {
//                SocketClient.connectServer(ip, this);true
//            }
//            showMsg(if (connectSocket) "Connection service" else "Close connection")
//            binding.btnConnectService.text = if (connectSocket) "Close the connection" else "Connect the service"
//        }

        binding.btnConnectService.setOnClickListener {
            val ip = binding.etIpAddress.text.toString()
            if (ip.isEmpty()) {
                showMsg("Please enter the IP address")
                return@setOnClickListener
            }
            connectSocket = if (connectSocket) {
                SocketClient.closeConnect()
                false
            } else {
                SocketClient.connectServer(ip, this)
                true
            }

            // Check if the connection is successful
            if (connectSocket) {
                showMsg("Connection service")
                binding.btnConnectService.text = "Close the connection"

                // Show the credentials dialog after successful connection
                showCredentialsDialog()
            } else {
                showMsg("Close connection")
                binding.btnConnectService.text = "Connect the service"
            }
        }
        //Send message to server/client
        binding.btnSendMsg.setOnClickListener {
            val msg = binding.etMsg.text.toString().trim()
            if (msg.isEmpty()) {
                showMsg("Please enter the information to be sent");return@setOnClickListener
            }
            //Check whether the message can be sent
            val isSend = if (openSocket) openSocket else if (connectSocket) connectSocket else false
            if (!isSend) {
                showMsg("The service is not currently open or connected to the service");return@setOnClickListener
            }
            if (isServer) SocketServer.sendToClient(msg) else SocketClient.sendToServer(msg)
            binding.etMsg.setText("")
            updateList(if (isServer) 1 else 2, msg)
        }
        //Initialization list
        msgAdapter = MsgAdapter(messages)
        binding.rvMsg.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = msgAdapter
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showCredentialsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_credentials, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val btnOk = dialogView.findViewById<Button>(R.id.btnOk)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Enter Credentials")
            .create()

        btnOk.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()

            // Check if username and password are not empty
            if (username.isNotEmpty() && password.isNotEmpty()) {
                val connectionMsg = "Connected on ${getCurrentDateTime()}" + "\n" + "Username: $username" + "\n" + "Password: $password"
                SocketClient.sendToServer(connectionMsg)

                // Dismiss the dialog
                dialog.dismiss()
            } else {
                // Show an error message or handle the case where username or password is empty
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentDateTime(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return currentDateTime.format(formatter)
    }

    private fun getIp() =
        intToIp((applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress)

    private fun intToIp(ip: Int) =
        "${(ip and 0xFF)}.${(ip shr 8 and 0xFF)}.${(ip shr 16 and 0xFF)}.${(ip shr 24 and 0xFF)}"

    /**
     * Receive messages from the client
     */
    override fun receiveClientMsg(success: Boolean, msg: String) = updateList(2, msg)

    /**
     * Receive messages from the server
     */
    override fun receiveServerMsg(msg: String) = updateList(1, msg)


    override fun otherMsg(msg: String) {
        Log.d(TAG, msg)
    }

    /**
     * update list
     */
    private fun updateList(type: Int, msg: String) {
        messages.add(Message(type, msg))
        runOnUiThread {
            (if (messages.size == 0) 0 else messages.size - 1).apply {
                msgAdapter.notifyItemChanged(this)
                binding.rvMsg.smoothScrollToPosition(this)
            }
        }
    }

    private fun showMsg(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}