package com.middepremkumar.autosilent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.middepremkumar.autosilent.databinding.ActivityDeveloperBinding

class DeveloperActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperBinding

    private val devName = "Prem Kumar Midde"
    private val devRole = "Android Developer"
    private val devAbout = "Built AutoSilent to automatically switch the phone to silent/vibrate during work or class hours, and back to normal afterward — no more forgetting to unmute."
    private val devEmail = "middepremkumar1@gmail.com"
    private val devGithub = "https://github.com/middepremkumar"
    private val devLinkedin = "https://www.linkedin.com/in/prem-kumar-midde-374a6133a/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)

        if (prefs.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        binding = ActivityDeveloperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvAvatarInitial.text = devName.trim().take(1).uppercase()

        // Looks for a drawable named "dev_photo" (dev_photo.png / .jpg) added to res/drawable.
        val photoResId = resources.getIdentifier("dev_photo", "drawable", packageName)
        if (photoResId != 0) {
            binding.ivAvatarPhoto.setImageResource(photoResId)
            binding.ivAvatarPhoto.visibility = View.VISIBLE
            binding.tvAvatarInitial.visibility = View.GONE
        }

        binding.tvDevName.text = devName
        binding.tvDevRole.text = devRole
        binding.tvDevAbout.text = devAbout
        binding.tvDevEmail.text = devEmail
        binding.tvDevGithub.text = devGithub.removePrefix("https://")
        binding.tvDevLinkedin.text = "linkedin.com/in/prem-kumar-midde-374a6133a/"

        binding.rowEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$devEmail")
            }
            startActivity(intent)
        }

        binding.rowGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(devGithub)))
        }

        binding.rowLinkedin.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(devLinkedin)))
        }
    }
}
