package com.example.contesttracker

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.contesttracker.update.ApkDownloader
import com.example.contesttracker.update.ApkInstaller
import com.example.contesttracker.update.UpdateManager
import com.example.contesttracker.update.UpdatePreferences
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val viewModel: ContestViewModel by viewModels()
    
    private var liveAdapter: ContestAdapter? = null
    private var upcomingAdapter: ContestAdapter? = null
    private var scheduleAdapter: ScheduleAdapter? = null
    private var remindersAdapter: ScheduleAdapter? = null
    
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var themeToggleButton: ImageButton
    
    private lateinit var homeLayout: View
    private lateinit var scheduleLayout: View
    private lateinit var remindersLayout: View
    private lateinit var settingsLayout: View
    
    private lateinit var emptyState: View
    private lateinit var remindersEmptyState: View
    private lateinit var homeEmptyState: View
    private lateinit var homeEmptyTitle: TextView
    private lateinit var homeEmptySubtitle: TextView
    private lateinit var liveNowLabel: View
    private lateinit var upcomingLabel: View
    private lateinit var staleBanner: TextView
    private lateinit var alarmPermissionBanner: TextView
    
    private val selectedPlatforms = Platform.entries.toMutableSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Read and apply theme preference before layout inflation
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val savedTheme = prefs.getString("app_theme", "system") ?: "system"
        applyAppTheme(savedTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            NotificationChannelManager.createNotificationChannel(this)
            checkNotificationPermission()
            setCurrentDate()
            initViews()
            setupAdapters()
            setupBottomNavigation()
            setupHomeUI()
            setupScheduleUI()
            setupRemindersUI()
            setupSettingsUI()
            bindViewModel()
            
            // Set quick theme toggle icon based on current UI configuration
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDark = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            themeToggleButton.setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_theme_toggle)
            
            themeToggleButton.setOnClickListener {
                val isCurrentlyDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val newTheme = if (isCurrentlyDark) "light" else "dark"
                prefs.edit().putString("app_theme", newTheme).apply()
                applyAppTheme(newTheme)
            }
            
            loadInitialData()

            // ── OTA Update System ──────────────────────────────────────────
            // Handle install intent forwarded by DownloadCompleteReceiver
            handleInstallIntent(intent)
            // Auto-check for updates at most once every 24 h (silent on error)
            UpdateManager.checkForUpdates(this)
            // ──────────────────────────────────────────────────────────────

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_startup), Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::homeLayout.isInitialized && homeLayout.isVisible) {
            liveAdapter?.startCountdown()
            upcomingAdapter?.startCountdown()
        }
    }

    override fun onStop() {
        super.onStop()
        liveAdapter?.stopCountdown()
        upcomingAdapter?.stopCountdown()
    }

    /**
     * Called when MainActivity is already at the top of the stack and a new
     * intent arrives (e.g. from DownloadCompleteReceiver after a download).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInstallIntent(intent)
    }

    /**
     * If the launching Intent carries [ApkDownloader.EXTRA_INSTALL_APK_PATH],
     * immediately prompt the system package installer.
     */
    private fun handleInstallIntent(intent: Intent?) {
        val apkPath = intent?.getStringExtra(ApkDownloader.EXTRA_INSTALL_APK_PATH)
            ?: return
        // Remove the extra so a configuration change doesn't re-trigger install
        intent.removeExtra(ApkDownloader.EXTRA_INSTALL_APK_PATH)
        val apkFile = File(apkPath)
        if (apkFile.exists()) {
            ApkInstaller.install(this, apkFile)
        }
    }

    private fun applyAppTheme(theme: String) {
        val mode = when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun checkNotificationPermission() {
        // ── POST_NOTIFICATIONS (Android 13+) ─────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun setCurrentDate() {
        val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        findViewById<TextView>(R.id.currentDateText)?.text = sdf.format(Date())
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        themeToggleButton = findViewById(R.id.themeToggleButton)
        
        homeLayout = findViewById(R.id.homeLayout)
        scheduleLayout = findViewById(R.id.scheduleLayout)
        remindersLayout = findViewById(R.id.remindersLayout)
        settingsLayout = findViewById(R.id.settingsLayout)
        
        emptyState = scheduleLayout.findViewById(R.id.scheduleEmptyState)
        remindersEmptyState = remindersLayout.findViewById(R.id.remindersEmptyState)

        homeEmptyState   = homeLayout.findViewById(R.id.homeEmptyState)
        homeEmptyTitle   = homeLayout.findViewById(R.id.homeEmptyTitle)
        homeEmptySubtitle = homeLayout.findViewById(R.id.homeEmptySubtitle)
        liveNowLabel          = homeLayout.findViewById(R.id.liveNowLabel)
        upcomingLabel         = homeLayout.findViewById(R.id.upcomingLabel)
        staleBanner           = homeLayout.findViewById(R.id.staleBanner)
        alarmPermissionBanner = homeLayout.findViewById(R.id.alarmPermissionBanner)

        val notificationButton: ImageButton = findViewById(R.id.notificationButton)
        notificationButton.setOnClickListener {
            bottomNavigation.selectedItemId = R.id.nav_reminders
        }
    }

    private fun setupAdapters() {
        liveAdapter     = ContestAdapter { url -> openContest(url) }
        upcomingAdapter = ContestAdapter { url -> openContest(url) }
        // Schedule tab: read-only calendar view, no bell toggles
        scheduleAdapter  = ScheduleAdapter(showReminderToggle = false, onContestClick = { url -> openContest(url) })
        // Reminders tab: per-contest bell toggle enabled
        remindersAdapter = ScheduleAdapter(showReminderToggle = true, onContestClick = { url -> openContest(url) })
    }

    private fun setupScheduleUI() {
        scheduleLayout.findViewById<RecyclerView>(R.id.scheduleRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = scheduleAdapter
        }
    }

    private fun setupRemindersUI() {
        remindersLayout.findViewById<RecyclerView>(R.id.remindersRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = remindersAdapter
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_contests -> switchScreen(homeLayout)
                R.id.nav_schedule -> switchScreen(scheduleLayout)
                R.id.nav_reminders -> switchScreen(remindersLayout)
                R.id.nav_settings -> switchScreen(settingsLayout)
                else -> false
            }
        }
    }

    private fun switchScreen(target: View): Boolean {
        homeLayout.isVisible = target == homeLayout
        scheduleLayout.isVisible = target == scheduleLayout
        remindersLayout.isVisible = target == remindersLayout
        settingsLayout.isVisible = target == settingsLayout
        
        if (target == homeLayout) {
            liveAdapter?.startCountdown()
            upcomingAdapter?.startCountdown()
        } else {
            liveAdapter?.stopCountdown()
            upcomingAdapter?.stopCountdown()
        }
        
        return true
    }

    private fun setupHomeUI() {
        findViewById<RecyclerView>(R.id.liveRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = liveAdapter
        }
        
        findViewById<RecyclerView>(R.id.upcomingRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = upcomingAdapter
        }

        val chipBg   = ContextCompat.getColorStateList(this, R.color.selector_chip_bg)
        val chipText = ContextCompat.getColorStateList(this, R.color.selector_chip_text)

        val platformChipGroup: ChipGroup = findViewById(R.id.platformChipGroup)

        val allChip = Chip(this).apply {
            text = "All"
            isCheckable = true
            isChecked = true
            chipBackgroundColor = chipBg
            setTextColor(chipText)
            chipStrokeWidth = 0f
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedPlatforms.addAll(Platform.entries)
                    for (i in 1 until platformChipGroup.childCount) {
                        (platformChipGroup.getChildAt(i) as Chip).isChecked = true
                    }
                } else {
                    selectedPlatforms.clear()
                    for (i in 1 until platformChipGroup.childCount) {
                        (platformChipGroup.getChildAt(i) as Chip).isChecked = false
                    }
                }
                applyFilters()
            }
        }
        platformChipGroup.addView(allChip)

        Platform.entries.forEach { platform ->
            val chip = Chip(this).apply {
                text = platform.displayName
                isCheckable = true
                isChecked = true
                chipBackgroundColor = chipBg
                setTextColor(chipText)
                chipStrokeWidth = 0f
                
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedPlatforms.add(platform) else selectedPlatforms.remove(platform)
                    (platformChipGroup.getChildAt(0) as? Chip)?.isChecked = selectedPlatforms.size == Platform.entries.size
                    applyFilters()
                }
            }
            platformChipGroup.addView(chip)
        }
    }

    private fun setupSettingsUI() {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val saveBtn: MaterialButton = settingsLayout.findViewById(R.id.saveSettingsButton)
        
        val switches = mapOf(
            Platform.CODEFORCES to settingsLayout.findViewById<MaterialSwitch>(R.id.switchCodeforces),
            Platform.LEETCODE to settingsLayout.findViewById<MaterialSwitch>(R.id.switchLeetCode),
            Platform.CODECHEF to settingsLayout.findViewById<MaterialSwitch>(R.id.switchCodeChef),
            Platform.ATCODER to settingsLayout.findViewById<MaterialSwitch>(R.id.switchAtCoder)
        )
        
        switches.forEach { (p, sw) ->
            sw.isChecked = prefs.getBoolean("platform_${p.name}", true)
        }

        val notificationSwitch = settingsLayout.findViewById<MaterialSwitch>(R.id.switchEnableNotifications)
        notificationSwitch.isChecked = prefs.getBoolean("enable_notifications", true)
        
        val btnFixAlarm = settingsLayout.findViewById<MaterialButton>(R.id.btnFixAlarmPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                btnFixAlarm.isVisible = true
                btnFixAlarm.setOnClickListener {
                    val exactAlarmIntent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(exactAlarmIntent)
                }
            } else {
                btnFixAlarm.isVisible = false
            }
        } else {
            btnFixAlarm.isVisible = false
        }

        // Theme Selection
        val themeChipGroup = settingsLayout.findViewById<ChipGroup>(R.id.themeChipGroup)
        val savedTheme = prefs.getString("app_theme", "system") ?: "system"
        when (savedTheme) {
            "light" -> themeChipGroup.check(R.id.chipThemeLight)
            "dark" -> themeChipGroup.check(R.id.chipThemeDark)
            else -> themeChipGroup.check(R.id.chipThemeSystem)
        }
        
        saveBtn.setOnClickListener {
            val enableNotifications = notificationSwitch.isChecked
            val selectedTheme = when (themeChipGroup.checkedChipId) {
                R.id.chipThemeLight -> "light"
                R.id.chipThemeDark -> "dark"
                else -> "system"
            }
            
            val edit = prefs.edit()
            edit.putBoolean("enable_notifications", enableNotifications)
            edit.putString("app_theme", selectedTheme)
            switches.forEach { (p, sw) ->
                edit.putBoolean("platform_${p.name}", sw.isChecked)
            }
            edit.apply()
            
            val scheduler = NotificationScheduler(this)
            if (!enableNotifications) {
                scheduler.cancelAll()
                // Also clear per-contest reminder prefs so re-enabling restores
                // the default opt-in state (all contests reminded).
                ReminderPreferences.clearAll(this)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = getSystemService(AlarmManager::class.java)
                    if (!alarmManager.canScheduleExactAlarms()) {
                        startActivity(Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        ))
                    }
                }

                // Immediately reschedule from the in-memory list, or fall back to the
                // on-disk cache so that alarms are set even if the upcoming API call fails.
                val contestsToSchedule = viewModel.contests.value
                    ?.takeIf { it.isNotEmpty() }
                    ?: scheduler.getCachedContests()
                if (contestsToSchedule.isNotEmpty()) {
                    scheduler.scheduleAll(contestsToSchedule)
                }
            }

            applyAppTheme(selectedTheme)
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
            viewModel.loadContests()
        }

        // ── App Updates section ────────────────────────────────────────────
        setupUpdateSettingsUI()
    }

    /**
     * Wires the "App Updates" card in the Settings screen:
     *  - Displays the installed version and last-check time.
     *  - Handles Stable / Beta channel selection.
     *  - Handles the "Check for Updates" button.
     */
    private fun setupUpdateSettingsUI() {
        val updatePrefs = UpdatePreferences(this)

        val tvCurrentVersion  = settingsLayout.findViewById<TextView>(R.id.tvCurrentVersion)
        val tvLastChecked     = settingsLayout.findViewById<TextView>(R.id.tvLastChecked)
        val btnCheckUpdates   = settingsLayout.findViewById<MaterialButton>(R.id.btnCheckForUpdates)
        val chipGroupChannel  = settingsLayout.findViewById<ChipGroup>(R.id.chipGroupUpdateChannel)
        val chipBeta          = settingsLayout.findViewById<Chip>(R.id.chipBeta)
        val chipStable        = settingsLayout.findViewById<Chip>(R.id.chipStable)

        // Show installed version
        tvCurrentVersion.text = try {
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        } catch (e: Exception) { "v1.0" }

        // Show last-check time
        tvLastChecked.text = updatePrefs.getFormattedLastCheckTime()

        // Restore channel selection
        if (updatePrefs.updateChannel == UpdatePreferences.CHANNEL_BETA) {
            chipBeta.isChecked = true
        } else {
            chipStable.isChecked = true
        }

        // Save channel when user switches chip
        chipGroupChannel.setOnCheckedStateChangeListener { _, checkedIds ->
            updatePrefs.updateChannel = when {
                checkedIds.contains(R.id.chipBeta) -> UpdatePreferences.CHANNEL_BETA
                else -> UpdatePreferences.CHANNEL_STABLE
            }
        }

        // "Check for Updates" button — force-checks ignoring the 24 h cooldown
        btnCheckUpdates.setOnClickListener {
            btnCheckUpdates.isEnabled = false
            btnCheckUpdates.text = "Checking\u2026"
            UpdateManager.checkForUpdatesManually(this) { result ->
                btnCheckUpdates.isEnabled = true
                btnCheckUpdates.text = getString(R.string.check_for_updates)
                tvLastChecked.text = updatePrefs.getFormattedLastCheckTime()
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun bindViewModel() {
        viewModel.contests.observe(this) { contests ->
            applyFilters(contests)
        }
        viewModel.isLoading.observe(this) { progressBar.isVisible = it }

        viewModel.errorMessage.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }

        // Show a tappable stale-data banner whenever cached data is displayed
        // instead of live data, so the user is never silently shown stale content.
        viewModel.isUsingCachedData.observe(this) { isStale ->
            staleBanner.isVisible = isStale
            if (isStale) {
                staleBanner.setOnClickListener {
                    viewModel.loadContests()
                }
            } else {
                staleBanner.setOnClickListener(null)
            }
        }

        // Show an alarm permission banner on Android 12+ when SCHEDULE_EXACT_ALARM
        // is not granted. Without it, notification delivery can be delayed by hours.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                alarmPermissionBanner.isVisible = true
                alarmPermissionBanner.setOnClickListener {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            } else {
                alarmPermissionBanner.isVisible = false
            }
        } else {
            alarmPermissionBanner.isVisible = false
        }
    }

    private fun applyFilters(contests: List<ContestModel>? = viewModel.contests.value) {
        val lAdapter = liveAdapter ?: return
        val uAdapter = upcomingAdapter ?: return
        val sAdapter = scheduleAdapter ?: return
        val rAdapter = remindersAdapter ?: return

        val filteredByPlatform = contests?.filter { it.platform?.let { p -> p in selectedPlatforms } == true } ?: emptyList()
        val now = System.currentTimeMillis()

        // Restrict home tab to today's contests only
        val todayKey = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val filteredByDayAndPlatform = filteredByPlatform.filter {
            val startMillis = ContestTimeUtils.startTimeMillis(it.start) ?: 0
            java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(startMillis)) == todayKey
        }

        val running = filteredByDayAndPlatform.filter {
            val start = ContestTimeUtils.startTimeMillis(it.start) ?: 0
            val end = start + (it.durationSeconds * 1000)
            now in start..end
        }
        val upcoming = filteredByDayAndPlatform.filter {
            (ContestTimeUtils.startTimeMillis(it.start) ?: 0) > now
        }

        lAdapter.submitList(running)
        uAdapter.submitList(upcoming)

        if (homeLayout.isVisible) {
            lAdapter.startCountdown()
            uAdapter.startCountdown()
        } else {
            lAdapter.stopCountdown()
            uAdapter.stopCountdown()
        }

        // ── Home empty state logic ────────────────────────────────────────────────
        val hasLive     = running.isNotEmpty()
        val hasUpcoming = upcoming.isNotEmpty()
        val hasVisibleContests = hasLive || hasUpcoming

        // Live Now section — only visible when contests are actually running right now
        liveNowLabel.isVisible = hasLive
        homeLayout.findViewById<View>(R.id.liveRecyclerView).isVisible = hasLive

        // Upcoming section — visible when there are upcoming contests today
        upcomingLabel.isVisible = hasUpcoming
        homeLayout.findViewById<View>(R.id.upcomingRecyclerView).isVisible = hasUpcoming

        if (!hasVisibleContests) {
            homeEmptyState.isVisible = true
            if (filteredByDayAndPlatform.isEmpty()) {
                // Nothing scheduled today for the selected platforms
                homeEmptyTitle.text   = getString(R.string.home_empty_no_contests_title)
                homeEmptySubtitle.text = getString(R.string.home_empty_no_contests_subtitle)
            } else {
                // There were contests today but they've all ended
                homeEmptyTitle.text   = getString(R.string.home_empty_all_done_title)
                homeEmptySubtitle.text = getString(R.string.home_empty_all_done_subtitle)
            }
        } else {
            homeEmptyState.isVisible = false
        }

        // ──────────────────────────────────────────────────────────────

        sAdapter.submitContests(filteredByPlatform)
        emptyState.isVisible = filteredByPlatform.isEmpty()
        
        val remindersList = filteredByDayAndPlatform.filter {
            val startMillis = ContestTimeUtils.startTimeMillis(it.start) ?: 0
            startMillis > now
        }
        rAdapter.submitContests(remindersList)
        
        val areNotificationsEnabled = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean("enable_notifications", true)
            
        if (!areNotificationsEnabled) {
            remindersEmptyState.isVisible = true
            remindersLayout.findViewById<TextView>(R.id.remindersEmptyTitle)?.text = getString(R.string.reminders_empty_no_notif_title)
            remindersLayout.findViewById<TextView>(R.id.remindersEmptySubtitle)?.text = getString(R.string.reminders_empty_no_notif_subtitle)
        } else if (remindersList.isEmpty()) {
            remindersEmptyState.isVisible = true
            remindersLayout.findViewById<TextView>(R.id.remindersEmptyTitle)?.text = getString(R.string.reminders_empty_title)
            remindersLayout.findViewById<TextView>(R.id.remindersEmptySubtitle)?.text = getString(R.string.reminders_empty_subtitle)
        } else {
            remindersEmptyState.isVisible = false
        }
    }


    private fun loadInitialData() {
        viewModel.loadContests()
    }

    private fun openContest(url: String) {
        runCatching {
            val normalizedUrl = if (url.startsWith("http")) url else "https://$url"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.error_browser), Toast.LENGTH_SHORT).show()
        }
    }
}
