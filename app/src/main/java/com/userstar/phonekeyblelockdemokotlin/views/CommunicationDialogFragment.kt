package com.userstar.phonekeyblelockdemokotlin.views


import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.userstar.phonekeyblelockdemokotlin.R

@SuppressLint("SetTextI18n")
class CommunicationDialogFragment : Fragment() {

    private lateinit var titleTextView: TextView
    private lateinit var dataTextView: TextView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_communication_dialog, container, false)

        titleTextView = view.findViewById(R.id.title_TextView)
        dataTextView = view.findViewById(R.id.data_TextView)
        view.findViewById<Button>(R.id.close_Button).setOnClickListener {
            dataTextView.text = ""
            hide()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        didCreatedCallback()
    }

    fun addLine(string: String) {
        dataTextView.text = "${dataTextView.text}\n$string"
    }

    private lateinit var manager: FragmentManager
    private lateinit var didCreatedCallback: () -> Unit
    fun create(manager: FragmentManager, didCreatedCallback: () -> Unit) {
        this.didCreatedCallback = didCreatedCallback
        this.manager = manager
        this.manager.beginTransaction()
            .add(R.id.fragment, this)
            .show(this)
            .commit()
    }

    fun show(title: String) {
        titleTextView.text = title
        manager.beginTransaction()
            .show(this)
            .commit()
    }

    fun hide() {
        manager.beginTransaction()
            .hide(this)
            .commit()
    }
}