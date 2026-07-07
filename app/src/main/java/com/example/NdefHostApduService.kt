package com.example

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * INTEGRATION CONTRACT for your existing UI:
 * - To set the link: write the URL string to SharedPreferences ("nfc_link_prefs" / "url").
 * - To toggle emulation on/off: call:
 *   packageManager.setComponentEnabledSetting(
 *       ComponentName(context, NdefHostApduService::class.java),
 *       COMPONENT_ENABLED_STATE_ENABLED or COMPONENT_ENABLED_STATE_DISABLED,
 *       DONT_KILL_APP
 *   )
 *   Default state counts as enabled.
 */
class NdefHostApduService : HostApduService() {

    companion object {
        private const val TAG = "NdefHostApduService"

        // AID constant
        private val AID_ANDROID_HCE = byteArrayOf(
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x85.toByte(), 0x01.toByte(), 0x01.toByte()
        )

        // Status words
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
        private val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        // CC file is this exact 15-byte hex constant: 000F20007F007F0406E1040FFF00FF
        private val CC_FILE = byteArrayOf(
            0x00.toByte(), 0x0F.toByte(), // CC file size (15 bytes)
            0x20.toByte(),                // Mapping version 2.0
            0x00.toByte(), 0x7F.toByte(), // MLe (max response data size, 127 bytes)
            0x00.toByte(), 0x7F.toByte(), // MLc (max command data size, 127 bytes)
            0x04.toByte(),                // NDEF File Control TLV Tag
            0x06.toByte(),                // NDEF File Control TLV Length (6 bytes)
            0xE1.toByte(), 0x04.toByte(), // NDEF File Identifier
            0x0F.toByte(), 0xFF.toByte(), // Max NDEF File Size (4095 bytes)
            0x00.toByte(),                // Read access condition (0x00 = free read)
            0xFF.toByte()                 // Write access condition (0xFF = read only)
        )

        private const val DEFAULT_URL = "https://ai.studio/build"
    }

    private var selectedFile = SelectedFile.NONE

    private enum class SelectedFile {
        NONE, CC, NDEF
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            return STATUS_FAILED
        }

        val apduHex = bytesToHex(commandApdu)
        Log.d(TAG, "processCommandApdu: $apduHex")

        if (commandApdu.size < 4) {
            return STATUS_FAILED
        }

        val cla = commandApdu[0]
        val ins = commandApdu[1]
        val p1 = commandApdu[2]
        val p2 = commandApdu[3]

        // 1. SELECT by AID (CLA=00 INS=A4 P1=04) for AID D2760000850101 -> respond 9000, reset selected-file state
        if (cla == 0x00.toByte() && ins == 0xA4.toByte() && p1 == 0x04.toByte()) {
            if (containsSubarray(commandApdu, AID_ANDROID_HCE)) {
                Log.d(TAG, "SELECT APPLICATION AID matched")
                selectedFile = SelectedFile.NONE
                return STATUS_SUCCESS
            }
            return STATUS_FILE_NOT_FOUND
        }

        // 2. SELECT file by ID (INS=A4 P1=00 Lc=02): E103 = Capability Container file, E104 = NDEF file. Remember which is selected -> 9000. Unknown ID -> 6A82.
        if (cla == 0x00.toByte() && ins == 0xA4.toByte() && p1 == 0x00.toByte()) {
            if (commandApdu.size >= 7) {
                val fileIdHigh = commandApdu[5]
                val fileIdLow = commandApdu[6]
                if (fileIdHigh == 0xE1.toByte() && fileIdLow == 0x03.toByte()) {
                    Log.d(TAG, "SELECT CC FILE matched")
                    selectedFile = SelectedFile.CC
                    return STATUS_SUCCESS
                } else if (fileIdHigh == 0xE1.toByte() && fileIdLow == 0x04.toByte()) {
                    Log.d(TAG, "SELECT NDEF FILE matched")
                    selectedFile = SelectedFile.NDEF
                    return STATUS_SUCCESS
                }
            }
            Log.d(TAG, "SELECT file by ID failed: unknown ID")
            return STATUS_FILE_NOT_FOUND
        }

        // 3. READ BINARY (INS=B0): offset = P1P2 (big-endian), length = Le (0 means 256)
        // Return that slice of the currently selected file + 9000. Must honor offset/length — readers fetch in chunks.
        if (cla == 0x00.toByte() && ins == 0xB0.toByte()) {
            val offset = (((p1.toInt() and 0xFF) shl 8) or (p2.toInt() and 0xFF))
            val le = if (commandApdu.size > 4) (commandApdu[4].toInt() and 0xFF) else 0
            // Standard: If Le is 0, read up to 256 bytes
            val reqLength = if (le == 0) 256 else le

            Log.d(TAG, "READ BINARY: Selected file = $selectedFile, offset = $offset, requested length = $reqLength")

            val fileBytes = when (selectedFile) {
                SelectedFile.CC -> CC_FILE
                SelectedFile.NDEF -> getNdefFilePayload()
                SelectedFile.NONE -> null
            }

            if (fileBytes == null) {
                Log.d(TAG, "READ BINARY failed: no file selected")
                return STATUS_FAILED
            }

            if (offset >= fileBytes.size) {
                Log.d(TAG, "READ BINARY failed: offset ($offset) >= file size (${fileBytes.size})")
                return STATUS_FAILED
            }

            val responseLength = minOf(reqLength, fileBytes.size - offset)
            val response = ByteArray(responseLength + 2)
            System.arraycopy(fileBytes, offset, response, 0, responseLength)
            response[responseLength] = 0x90.toByte()
            response[responseLength + 1] = 0x00.toByte()
            return response
        }

        // Anything else -> 6F00
        return STATUS_FAILED
    }

    private fun containsSubarray(array: ByteArray, subarray: ByteArray): Boolean {
        if (array.size < subarray.size) return false
        for (i in 0..array.size - subarray.size) {
            var found = true
            for (j in subarray.indices) {
                if (array[i + j] != subarray[j]) {
                    found = false
                    break
                }
            }
            if (found) return true
        }
        return false
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated: reason = $reason")
        selectedFile = SelectedFile.NONE
    }

    private fun getNdefFilePayload(): ByteArray {
        val sharedPrefs = getSharedPreferences("nfc_link_prefs", Context.MODE_PRIVATE)
        val url = sharedPrefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL

        val ndefMessage = try {
            NdefMessage(NdefRecord.createUri(url))
        } catch (e: Exception) {
            NdefMessage(NdefRecord.createUri(DEFAULT_URL))
        }
        val ndefBytes = ndefMessage.toByteArray()
        val ndefLength = ndefBytes.size

        val ndefFile = ByteArray(ndefLength + 2)
        ndefFile[0] = ((ndefLength shr 8) and 0xFF).toByte()
        ndefFile[1] = (ndefLength and 0xFF).toByte()
        System.arraycopy(ndefBytes, 0, ndefFile, 2, ndefLength)
        return ndefFile
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
            result.append(" ")
        }
        return result.toString().trim()
    }
}
