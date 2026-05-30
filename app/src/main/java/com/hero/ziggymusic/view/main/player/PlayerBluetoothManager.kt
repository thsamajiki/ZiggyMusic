package com.hero.ziggymusic.view.main.player

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hero.ziggymusic.R
import java.util.Locale

class PlayerBluetoothManager(
    private val fragment: Fragment,
    private val audioManager: AudioManager,
    private val rootViewProvider: () -> View?,
    private val setBluetoothIcon: (Int) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val updateBluetoothRunnable = Runnable {
        updateBluetoothIcon()
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any { it.isBluetoothOrWiredAudioDevice() }) {
                updateBluetoothIcon()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.isBluetoothOrWiredAudioDevice() }) {
                updateBluetoothIcon()
            }
        }
    }

    private val bluetoothPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            if (grantResults.isNotEmpty() && grantResults.values.all { it }) {
                handleBluetoothClick()
            } else {
                onMessage("블루투스 권한이 필요합니다.")
            }
        }

    private val companionDeviceChooserLauncher: ActivityResultLauncher<IntentSenderRequest> =
        fragment.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val selectedDevice = result.data?.extractSelectedBluetoothDevice()
            if (selectedDevice == null) {
                onMessage("선택한 블루투스 기기를 확인할 수 없습니다.")
                return@registerForActivityResult
            }

            requestBluetoothPairing(selectedDevice)
        }

    private val bluetoothBondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

            val device = intent.getBluetoothDeviceExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            val pendingAddress = pendingPairingDeviceAddress ?: return
            if (!isPairingTarget(device, pendingAddress)) return

            val deviceName = getBluetoothDeviceLogName(device)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            when (bondState) {
                BluetoothDevice.BOND_BONDING -> {
                    Log.d(LOG_TAG, "Bluetooth pairing in progress: $deviceName ($pendingAddress)")
                }

                BluetoothDevice.BOND_BONDED -> {
                    if (!hasActiveView()) return
                    setBluetoothIcon(R.drawable.ic_airpods)
                    pendingPairingDeviceAddress = null
                    onMessage("블루투스 페어링이 완료되었습니다.")
                    Log.d(LOG_TAG, "Bluetooth pairing completed: $deviceName ($pendingAddress)")
                }

                BluetoothDevice.BOND_NONE -> {
                    pendingPairingDeviceAddress = null
                    onMessage("블루투스 페어링에 실패했습니다.")
                    Log.w(LOG_TAG, "Bluetooth pairing failed or canceled: $deviceName ($pendingAddress)")
                    updateBluetoothIcon()
                }
            }
        }
    }

    private val companionDeviceManagerCallback = object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            launchCompanionDeviceChooser(intentSender)
        }

        @Deprecated("onDeviceFound was renamed to onAssociationPending in API 33.")
        override fun onDeviceFound(intentSender: IntentSender) {
            launchCompanionDeviceChooser(intentSender)
        }

        override fun onFailure(error: CharSequence?) {
            val errorMessage = error?.toString()
            if (errorMessage.isCompanionDeviceChooserCancellation()) return

            Log.w(LOG_TAG, "Companion device association failed: $errorMessage")
            onMessage("블루투스 기기 목록을 불러오지 못했습니다.")
        }
    }

    private var pendingPairingDeviceAddress: String? = null
    private var isBluetoothBondStateReceiverRegistered = false
    private var isAudioDeviceCallbackRegistered = false

    fun handleBluetoothClick() {
        val bluetoothAdapter = getBluetoothAdapter()
        if (bluetoothAdapter == null) {
            onMessage("이 기기는 블루투스를 지원하지 않습니다.")
            return
        }

        val missingPermissions = getMissingBluetoothPermissions()
        if (missingPermissions.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            onMessage("블루투스를 켜주세요.")
            return
        }

        showBluetoothDeviceChooser()
    }

    fun updateBluetoothIcon() {
        if (!hasActiveView()) return

        try {
            val hasBluetoothDevice = isBluetoothAudioDeviceConnected()
            val hasWiredDevice = isWiredAudioDeviceConnected()

            if (hasBluetoothDevice && !hasWiredDevice) {
                setBluetoothIcon(R.drawable.ic_airpods)
                return
            }

            if (hasBluetoothConnectPermission()) {
                val bluetoothManager = getBluetoothManager()
                val bluetoothAdapter = bluetoothManager?.adapter

                if (bluetoothAdapter?.isEnabled == true && bluetoothManager != null) {
                    val isBluetoothConnected = checkBluetoothConnection(bluetoothManager)

                    if (isBluetoothConnected) {
                        setBluetoothIcon(R.drawable.ic_airpods)
                        return
                    }
                }
            }

            setBluetoothIcon(R.drawable.ic_airplay)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error updating bluetooth icon", e)
            setBluetoothIcon(R.drawable.ic_airplay)
        }
    }

    fun refreshBluetoothIcon() {
        val rootView = rootViewProvider() ?: return

        rootView.removeCallbacks(updateBluetoothRunnable)
        rootView.postDelayed(updateBluetoothRunnable, BLUETOOTH_STATUS_UPDATE_DELAY_MS)
    }

    fun registerAudioDeviceCallback() {
        if (isAudioDeviceCallbackRegistered) return

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        isAudioDeviceCallbackRegistered = true
    }

    fun unregisterAudioDeviceCallback() {
        if (!isAudioDeviceCallbackRegistered) return

        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        isAudioDeviceCallbackRegistered = false
    }

    fun release() {
        rootViewProvider()?.removeCallbacks(updateBluetoothRunnable)
        unregisterAudioDeviceCallback()
        unregisterBluetoothBondStateReceiver()
        bluetoothPermissionLauncher.unregister()
        companionDeviceChooserLauncher.unregister()
    }

    private fun getBluetoothManager(): BluetoothManager? {
        return ContextCompat.getSystemService(
            fragment.requireContext(),
            BluetoothManager::class.java
        )
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        return getBluetoothManager()?.adapter
    }

    private fun getMissingBluetoothPermissions(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()

        return listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ).filter { permission ->
            ActivityCompat.checkSelfPermission(fragment.requireContext(), permission) !=
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(
                    fragment.requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(
                    fragment.requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showBluetoothDeviceChooser() {
        val companionDeviceManager = ContextCompat.getSystemService(
            fragment.requireContext(),
            CompanionDeviceManager::class.java
        )

        if (companionDeviceManager == null) {
            onMessage("블루투스 기기 목록을 불러올 수 없습니다.")
            return
        }

        val requestBuilder = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestBuilder.setDisplayName("ZiggyMusic")
        }

        val request = requestBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionDeviceManager.associate(
                request,
                ContextCompat.getMainExecutor(fragment.requireContext()),
                companionDeviceManagerCallback
            )
        } else {
            companionDeviceManager.associate(
                request,
                companionDeviceManagerCallback,
                Handler(Looper.getMainLooper())
            )
        }
    }

    private fun launchCompanionDeviceChooser(intentSender: IntentSender) {
        try {
            val request = IntentSenderRequest.Builder(intentSender).build()
            companionDeviceChooserLauncher.launch(request)
        } catch (e: IntentSender.SendIntentException) {
            Log.e(LOG_TAG, "Failed to launch companion device chooser", e)
            onMessage("블루투스 기기 목록을 열지 못했습니다.")
        }
    }

    private fun requestBluetoothPairing(device: BluetoothDevice) {
        if (!hasActiveView()) return

        if (getMissingBluetoothPermissions().isNotEmpty()) {
            bluetoothPermissionLauncher.launch(getMissingBluetoothPermissions().toTypedArray())
            return
        }

        registerBluetoothBondStateReceiver()
        val deviceAddress = getBluetoothDeviceAddressIfPermitted(device) ?: run {
            onMessage("블루투스 기기 정보를 확인할 수 없습니다.")
            return
        }

        pendingPairingDeviceAddress = deviceAddress
        cancelBluetoothDiscoveryIfPermitted()

        when (getBluetoothBondStateIfPermitted(device)) {
            null -> {
                pendingPairingDeviceAddress = null
                onMessage("블루투스 권한이 필요합니다.")
            }

            BluetoothDevice.BOND_BONDED -> {
                if (isBluetoothAudioDeviceConnected()) {
                    setBluetoothIcon(R.drawable.ic_airpods)
                } else {
                    onMessage("이미 페어링된 기기입니다. 블루투스 설정에서 연결해주세요.")
                    openBluetoothSettings()
                    updateBluetoothIcon()
                }
                pendingPairingDeviceAddress = null
                Log.d(LOG_TAG, "Bluetooth device is already paired: $deviceAddress")
            }

            BluetoothDevice.BOND_BONDING -> {
                Log.d(LOG_TAG, "Bluetooth device is already bonding: $deviceAddress")
            }

            else -> {
                val pairingStarted = createBluetoothBondIfPermitted(device)
                Log.d(LOG_TAG, "Bluetooth pairing requested: $deviceAddress, started=$pairingStarted")
                if (!pairingStarted) {
                    pendingPairingDeviceAddress = null
                    onMessage("블루투스 페어링을 시작하지 못했습니다.")
                    updateBluetoothIcon()
                }
            }
        }
    }

    private fun getBluetoothDeviceAddressIfPermitted(device: BluetoothDevice): String? {
        if (!hasBluetoothConnectPermission()) return null

        return try {
            device.address
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Missing permission while reading bluetooth device address", e)
            null
        }
    }

    private fun getBluetoothDeviceLogName(device: BluetoothDevice): String {
        if (!hasBluetoothConnectPermission()) return "Unknown device"

        return try {
            device.name ?: "Unknown device"
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Missing permission while reading bluetooth device name", e)
            "Unknown device"
        }
    }

    private fun getBluetoothBondStateIfPermitted(device: BluetoothDevice): Int? {
        if (!hasBluetoothConnectPermission()) return null

        return try {
            device.bondState
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Missing permission while reading bluetooth bond state", e)
            null
        }
    }

    private fun createBluetoothBondIfPermitted(device: BluetoothDevice): Boolean {
        if (!hasBluetoothConnectPermission()) return false

        return try {
            device.createBond()
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Missing permission while creating bluetooth bond", e)
            false
        }
    }

    private fun isPairingTarget(device: BluetoothDevice, pendingAddress: String): Boolean {
        return getBluetoothDeviceAddressIfPermitted(device) == pendingAddress
    }

    private fun cancelBluetoothDiscoveryIfPermitted() {
        if (!hasBluetoothScanPermission()) return

        try {
            getBluetoothAdapter()?.cancelDiscovery()
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Missing permission while canceling bluetooth discovery", e)
        }
    }

    private fun openBluetoothSettings() {
        runCatching {
            fragment.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }.onFailure { e ->
            Log.e(LOG_TAG, "Failed to open bluetooth settings", e)
        }
    }

    private fun registerBluetoothBondStateReceiver() {
        if (isBluetoothBondStateReceiverRegistered) return

        ContextCompat.registerReceiver(
            fragment.requireContext(),
            bluetoothBondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        isBluetoothBondStateReceiverRegistered = true
    }

    private fun unregisterBluetoothBondStateReceiver() {
        if (!isBluetoothBondStateReceiverRegistered) return

        fragment.requireContext().unregisterReceiver(bluetoothBondStateReceiver)
        isBluetoothBondStateReceiverRegistered = false
    }

    private fun Intent.extractSelectedBluetoothDevice(): BluetoothDevice? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val associatedDevice = getAssociationInfoExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION
            )?.associatedDevice

            val selectedDevice = associatedDevice?.bluetoothDevice
                ?: associatedDevice?.bleDevice?.device

            if (selectedDevice != null) return selectedDevice
        }

        @Suppress("DEPRECATION")
        val legacyDevice = getBluetoothDeviceExtra(CompanionDeviceManager.EXTRA_DEVICE)
        if (legacyDevice != null) return legacyDevice

        @Suppress("DEPRECATION")
        val legacyLeDevice = getBleScanResultExtra(
            CompanionDeviceManager.EXTRA_DEVICE
        )?.device

        return legacyLeDevice
    }

    private fun Intent.getBluetoothDeviceExtra(name: String): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }

    private fun Intent.getAssociationInfoExtra(name: String): AssociationInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, AssociationInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }

    private fun Intent.getBleScanResultExtra(name: String): ScanResult? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, ScanResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }

    private fun String?.isCompanionDeviceChooserCancellation(): Boolean {
        if (isNullOrBlank()) return false

        val normalizedMessage = lowercase(Locale.US)
        return "cancel" in normalizedMessage ||
                "cancelled" in normalizedMessage ||
                "canceled" in normalizedMessage
    }

    private fun checkBluetoothConnection(bluetoothManager: BluetoothManager): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                fragment.requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        try {
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

            val a2dpConnected =
                bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED
            val headsetConnected =
                bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED

            if (a2dpConnected || headsetConnected) {
                Log.d(
                    LOG_TAG,
                    "Audio Profile connected: A2DP=$a2dpConnected, HEADSET=$headsetConnected"
                )
                return true
            }
        } catch (e: SecurityException) {
            Log.e(LOG_TAG, "Missing permission while checking bluetooth profile connection", e)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error checking bluetooth connection", e)
        }

        return false
    }

    private fun isBluetoothAudioDeviceConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    -> {
                    return true
                }

                else -> {}
            }
        }

        return false
    }

    private fun isWiredAudioDeviceConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    -> {
                    return true
                }

                else -> {}
            }
        }

        return false
    }

    private fun AudioDeviceInfo.isBluetoothOrWiredAudioDevice(): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
                -> true

            else -> false
        }
    }

    private fun hasActiveView(): Boolean {
        return rootViewProvider() != null
    }

    private companion object {
        const val LOG_TAG = "Bluetooth"
        const val BLUETOOTH_STATUS_UPDATE_DELAY_MS = 1_000L
    }
}
