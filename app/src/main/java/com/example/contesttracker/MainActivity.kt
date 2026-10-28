package com.example.contesttracker

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
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
    private lateinit var batteryOptBanner: TextView
    private lateinit var platformChipGroup: ChipGroup

    // Re-run applyFilters() periodically to keep live / upcoming state current
    private val filterRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val filterRefreshRunnable = object : Runnable {
        override fun run() {
            applyFilters()
            filterRefreshHandler.postDelayed(this, FILTER_REFRESH_INTERVAL_MS)
        }
    }

    private var isSyncingPlatforms = false
    private val selectedPlatforms = mutableSetOf<Platform>()
    private val platformChips = mutableMapOf<Platform, Chip>()
    private var allChip: Chip? = null
    private val platformSwitches = mutableMapOf<Platform, MaterialSwitch>()

    private fun loadSelectedPlatforms() {
        selectedPlatforms.clear()
        selectedPlatforms.addAll(PlatformPreferences.getSelectedPlatforms(this))
    }

    private fun syncChipsAndSwitches() {
        isSyncingPlatforms = true
        try {
            val allSelected = PlatformPreferences.areAllPlatformsSelected(selectedPlatforms)
            allChip?.isChecked = allSelected
            Platform.entries.forEach { platform ->
                val isSelected = selectedPlatforms.contains(platform)
                platformChips[platform]?.isChecked = isSelected
                platformSwitches[platform]?.isChecked = isSelected
            }
        } finally {
            isSyncingPlatforms = false
        }
    }

    private fun setPlatformEnabled(platform: Platform, enabled: Boolean) {
        if (enabled) {
            selectedPlatforms.add(platform)
        } else {
            selectedPlatforms.remove(platform)
        }
        PlatformPreferences.setPlatformEnabled(this, platform, enabled)
        syncChipsAndSwitches()
        rescheduleNotifications()
        applyFilters()
    }

    private fun setAllPlatformsEnabled(enabled: Boolean) {
        if (enabled) {
            selectedPlatforms.addAll(Platform.entries)
        } else {
            selectedPlatforms.clear()
        }
        PlatformPreferences.setAllPlatforms(this, enabled)
        syncChipsAndSwitches()
        rescheduleNotifications()
        applyFilters()
    }

    private fun rescheduleNotifications() {
        val areNotificationsEnabled = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean("enable_notifications", true)
        if (!areNotificationsEnabled) return

        val scheduler = NotificationScheduler(this)
        val contests = viewModel.contests.value?.takeIf { it.isNotEmpty() } ?: scheduler.getCachedContests()
        if (contests.isNotEmpty()) {
            scheduler.scheduleAll(contests)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Read and apply theme preference before layout inflation
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val savedTheme = prefs.getString("app_theme", "system") ?: "system"
        applyAppTheme(savedTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            loadSelectedPlatforms()
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
            syncChipsAndSwitches()
            bindViewModel()
            
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

            // Daily background alarm
            DailyRefreshScheduler.schedule(this)

            // OTA Update System
            handleInstallIntent(intent)
            UpdateManager.checkForUpdates(this)

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
        filterRefreshHandler.postDelayed(filterRefreshRunnable, FILTER_REFRESH_INTERVAL_MS)
    }

    override fun onStop() {
        super.onStop()
        liveAdapter?.stopCountdown()
        upcomingAdapter?.stopCountdown()
        filterRefreshHandler.removeCallbacks(filterRefreshRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (!::alarmPermissionBanner.isInitialized) return

        val hadPermission = !alarmPermissionBanner.isVisible
        updateAlarmPermissionBanner()
        val hasPermissionNow = !alarmPermissionBanner.isVisible

        if (!hadPermission && hasPermissionNow) {
            val scheduler = NotificationScheduler(this)
            val contests = viewModel.contests.value
                ?.takeIf { it.isNotEmpty() }
                ?: scheduler.getCachedContests()
            if (contests.isNotEmpty()) {
                scheduler.scheduleAll(contests)
            }
        }

        if (::batteryOptBanner.isInitialized) {
            updateBatteryOptBanner()
        }
    }

    private fun updateAlarmPermissionBanner() {
        val notificationsEnabled = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean("enable_notifications", true)
        if (!notificationsEnabled) {
            alarmPermissionBanner.isVisible = false
            return
        }

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
                alarmPermissionBanner.setOnClickListener(null)
            }
        } else {
            alarmPermissionBanner.isVisible = false
        }
    }

    private fun updateBatteryOptBanner() {
        val notificationsEnabled = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean("enable_notifications", true)
        if (!notificationsEnabled) {
            batteryOptBanner.isVisible = false
            return
        }

        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            batteryOptBanner.isVisible = true
            batteryOptBanner.setOnClickListener {
                runCatching {
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        } else {
            batteryOptBanner.isVisible = false
            batteryOptBanner.setOnClickListener(null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInstallIntent(intent)
    }

    private fun handleInstallIntent(intent: Intent?) {
        val apkPath = intent?.getStringExtra(ApkDownloader.EXTRA_INSTALL_APK_PATH)
            ?: return
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
        batteryOptBanner      = homeLayout.findViewById(R.id.batteryOptBanner)

        val notificationButton: ImageButton = findViewById(R.id.notificationButton)
        notificationButton.setOnClickListener {
            bottomNavigation.selectedItemId = R.id.nav_reminders
        }
    }

    private fun setupAdapters() {
        val reminderToggleHandler: (ContestModel, Boolean) -> Unit = { contest, enabled ->
            onReminderToggled(contest, enabled)
        }

        liveAdapter = ContestAdapter(
            onOpenContest = { url -> openContest(url) },
            onReminderToggle = reminderToggleHandler
        )
        upcomingAdapter = ContestAdapter(
            onOpenContest = { url -> openContest(url) },
            onReminderToggle = reminderToggleHandler
        )
        // Both Schedule and Reminders tabs have reminder toggles enabled
        scheduleAdapter = ScheduleAdapter(
            showReminderToggle = true,
            onContestClick = { url -> openContest(url) },
            onReminderToggle = reminderToggleHandler
        )
        remindersAdapter = ScheduleAdapter(
            showReminderToggle = true,
            onContestClick = { url -> openContest(url) },
            onReminderToggle = reminderToggleHandler
        )
    }

    private fun onReminderToggled(contest: ContestModel, enabled: Boolean) {
        ReminderPreferences.setEnabled(this, contest.id, enabled)
        val scheduler = NotificationScheduler(this)
        val contests = viewModel.contests.value?.takeIf { it.isNotEmpty() } ?: scheduler.getCachedContests()
        if (contests.isNotEmpty()) {
            scheduler.scheduleAll(contests)
        }
        liveAdapter?.notifyDataSetChanged()
        upcomingAdapter?.notifyDataSetChanged()
        scheduleAdapter?.refreshItems()
        remindersAdapter?.refreshItems()
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
        
        syncChipsAndSwitches()

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

        val chipBg     = ContextCompat.getColorStateList(this, R.color.selector_chip_bg)
        val chipText   = ContextCompat.getColorStateList(this, R.color.selector_chip_text)
        val chipStroke = ContextCompat.getColorStateList(this, R.color.selector_chip_stroke)

        platformChipGroup = findViewById(R.id.platformChipGroup)
        platformChipGroup.removeAllViews()
        platformChips.clear()

        val strokeWidthPx  = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
        val cornerRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)

        allChip = Chip(this).apply {
            text = "All"
            isCheckable = true
            isChecked = PlatformPreferences.areAllPlatformsSelected(selectedPlatforms)
            chipBackgroundColor = chipBg
            setTextColor(chipText)
            chipStrokeColor = chipStroke
            chipStrokeWidth = strokeWidthPx
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(cornerRadiusPx)
                .build()
            setOnCheckedChangeListener { _, isChecked ->
                if (isSyncingPlatforms) return@setOnCheckedChangeListener
                setAllPlatformsEnabled(isChecked)
            }
        }
        platformChipGroup.addView(allChip)

        Platform.entries.forEach { platform ->
            val chip = Chip(this).apply {
                text = platform.displayName
                isCheckable = true
                isChecked = selectedPlatforms.contains(platform)
                chipBackgroundColor = chipBg
                setTextColor(chipText)
                chipStrokeColor = chipStroke
                chipStrokeWidth = strokeWidthPx
                chipIcon = ContextCompat.getDrawable(this@MainActivity, platform.logoResId)
                isChipIconVisible = true
                chipIconTint = null
                chipIconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(cornerRadiusPx)
                    .build()
                
                setOnCheckedChangeListener { _, isChecked ->
                    if (isSyncingPlatforms) return@setOnCheckedChangeListener
                    setPlatformEnabled(platform, isChecked)
                }
            }
            platformChips[platform] = chip
            platformChipGroup.addView(chip)
        }
    }

    private fun setupSettingsUI() {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val saveBtn: MaterialButton = settingsLayout.findViewById(R.id.saveSettingsButton)
        
        platformSwitches.clear()
        platformSwitches[Platform.CODEFORCES] = settingsLayout.findViewById(R.id.switchCodeforces)
        platformSwitches[Platform.LEETCODE]   = settingsLayout.findViewById(R.id.switchLeetCode)
        platformSwitches[Platform.CODECHEF]   = settingsLayout.findViewById(R.id.switchCodeChef)
        platformSwitches[Platform.ATCODER]    = settingsLayout.findViewById(R.id.switchAtCoder)

        platformSwitches.forEach { (platform, switchView) ->
            switchView.isChecked = selectedPlatforms.contains(platform)
            switchView.setOnCheckedChangeListener { _, isChecked ->
                if (isSyncingPlatforms) return@setOnCheckedChangeListener
                setPlatformEnabled(platform, isChecked)
            }
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
            platformSwitches.forEach { (p, sw) ->
                edit.putBoolean(PlatformPreferences.key(p), sw.isChecked)
            }
            edit.apply()

            // Update selectedPlatforms and synchronize Home chips with new settings
            selectedPlatforms.clear()
            platformSwitches.forEach { (p, sw) ->
                if (sw.isChecked) selectedPlatforms.add(p)
            }
            syncChipsAndSwitches()
            
            val scheduler = NotificationScheduler(this)
            if (!enableNotifications) {
                scheduler.cancelAll()
                ReminderPreferences.clearAll(this)
                DailyRefreshScheduler.cancel(this)
            } else {
                DailyRefreshScheduler.schedule(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = getSystemService(AlarmManager::class.java)
                    if (!alarmManager.canScheduleExactAlarms()) {
                        startActivity(Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        ))
                    }
                }

                val contestsToSchedule = viewModel.contests.value
                    ?.takeIf { it.isNotEmpty() }
                    ?: scheduler.getCachedContests()
                if (contestsToSchedule.isNotEmpty()) {
                    scheduler.scheduleAll(contestsToSchedule)
                }
            }

            applyAppTheme(selectedTheme)
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
            applyFilters()
            viewModel.loadContests()
        }

        setupUpdateSettingsUI()

        settingsLayout.findViewById<android.widget.TextView>(R.id.versionInfoText)
            ?.text = "Version ${BuildConfig.VERSION_NAME}"
    }

    private fun setupUpdateSettingsUI() {
        val updatePrefs = UpdatePreferences(this)

        val tvCurrentVersion  = settingsLayout.findViewById<TextView>(R.id.tvCurrentVersion)
        val tvLastChecked     = settingsLayout.findViewById<TextView>(R.id.tvLastChecked)
        val btnCheckUpdates   = settingsLayout.findViewById<MaterialButton>(R.id.btnCheckForUpdates)
        val chipGroupChannel  = settingsLayout.findViewById<ChipGroup>(R.id.chipGroupUpdateChannel)
        val chipBeta          = settingsLayout.findViewById<Chip>(R.id.chipBeta)
        val chipStable        = settingsLayout.findViewById<Chip>(R.id.chipStable)

        tvCurrentVersion.text = try {
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        } catch (e: Exception) { "v1.0" }

        tvLastChecked.text = updatePrefs.getFormattedLastCheckTime()

        if (updatePrefs.updateChannel == UpdatePreferences.CHANNEL_BETA) {
            chipBeta.isChecked = true
        } else {
            chipStable.isChecked = true
        }

        chipGroupChannel.setOnCheckedStateChangeListener { _, checkedIds ->
            updatePrefs.updateChannel = when {
                checkedIds.contains(R.id.chipBeta) -> UpdatePreferences.CHANNEL_BETA
                else -> UpdatePreferences.CHANNEL_STABLE
            }
        }

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

        updateAlarmPermissionBanner()
        updateBatteryOptBanner()
    }

    private fun applyFilters(contests: List<ContestModel>? = viewModel.contests.value) {
        val lAdapter = liveAdapter ?: return
        val uAdapter = upcomingAdapter ?: return
        val sAdapter = scheduleAdapter ?: return
        val rAdapter = remindersAdapter ?: return

        val filteredByPlatform = contests?.filter { it.platform?.let { p -> p in selectedPlatforms } == true } ?: emptyList()
        val now = System.currentTimeMillis()

        val dayFmt   = SimpleDateFormat("yyyyMMdd", Locale.US)
        val todayKey = dayFmt.format(Date(now))

        // Live Now: all active contests (including any started yesterday before midnight)
        val running = filteredByPlatform.filter {
            now in it.startMillis..it.endMillis
        }

        // Upcoming today: contests scheduled for later today
        val upcoming = filteredByPlatform.filter {
            it.startMillis > now && dayFmt.format(Date(it.startMillis)) == todayKey
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

        // Home empty state logic
        val hasLive = running.isNotEmpty()
        val hasUpcoming = upcoming.isNotEmpty()
        val hasVisibleContests = hasLive || hasUpcoming

        liveNowLabel.isVisible = hasLive
        homeLayout.findViewById<View>(R.id.liveRecyclerView).isVisible = hasLive

        upcomingLabel.isVisible = hasUpcoming
        homeLayout.findViewById<View>(R.id.upcomingRecyclerView).isVisible = hasUpcoming

        if (!hasVisibleContests) {
            homeEmptyState.isVisible = true
            val anyContestsToday = filteredByPlatform.any {
                dayFmt.format(Date(it.startMillis)) == todayKey
            }
            if (!anyContestsToday) {
                homeEmptyTitle.text = getString(R.string.home_empty_no_contests_title)
                homeEmptySubtitle.text = getString(R.string.home_empty_no_contests_subtitle)
            } else {
                homeEmptyTitle.text = getString(R.string.home_empty_all_done_title)
                homeEmptySubtitle.text = getString(R.string.home_empty_all_done_subtitle)
            }
        } else {
            homeEmptyState.isVisible = false
        }

        // Schedule tab: all contests for the selected platforms
        sAdapter.submitContests(filteredByPlatform)
        emptyState.isVisible = filteredByPlatform.isEmpty()
        
        // Reminders tab: all upcoming contests across all dates with full alarm control!
        val remindersList = filteredByPlatform.filter { it.startMillis > now }
        rAdapter.submitContests(remindersList)
        
        val areNotificationsEnabled = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean("enable_notifications", true)
            
        if (!areNotificationsEnabled) {
            remindersEmptyState.isVisible = true
            remindersLayout.findViewById<TextView>(R.id.remindersEmptyTitle)?.text = getString(R.string.reminders_empty_no_notif_title)
            remindersLayout.findViewById<TextView>(R.id.remindersEmptySubtitle)?.text = getString(R.string.reminders_empty_no_notif_subtitle)
        } else if (remindersList.isEmpty()) {
            remindersEmptyState.isVisible = true
            remindersLayout.findViewById<TextView>(R.id.remindersEmptyTitle)?.text = getString(R.string.no_contests_scheduled)
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
            val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.error_browser), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val FILTER_REFRESH_INTERVAL_MS = 60_000L
    }
}
