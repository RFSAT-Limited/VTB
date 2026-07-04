package com.rfsat.vtb.about

import android.os.Bundle
import com.rfsat.vtb.ui.BaseActivity
import com.rfsat.vtb.BuildConfig
import com.rfsat.vtb.databinding.ActivityAboutBinding

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvVersion.text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
    }
}
