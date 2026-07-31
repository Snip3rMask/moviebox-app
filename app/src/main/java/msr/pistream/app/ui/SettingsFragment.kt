package msr.pistream.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import msr.pistream.app.R

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            val info = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
            view.findViewById<TextView>(R.id.versionValue).text = info.versionName ?: "—"
            view.findViewById<TextView>(R.id.packageValue).text = requireContext().packageName
        } catch (_: Exception) {
            // package info unavailable — keep placeholders
        }
    }
}
