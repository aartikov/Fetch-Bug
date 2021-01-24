package me.aartikov.fetchbug

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class Pref(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("pref", Context.MODE_PRIVATE)

    var directoryUri: Uri?
        get() = sharedPreferences.getString("directoryUri", null)?.let { Uri.parse(it) }
        set(value) {
            sharedPreferences.edit {
                if (value != null) {
                    putString("directoryUri", value.toString())
                } else {
                    remove("directoryUri")
                }
            }
        }

    var fileUri: Uri?
        get() = sharedPreferences.getString("fileUri", null)?.let { Uri.parse(it) }
        set(value) {
            sharedPreferences.edit {
                if (value != null) {
                    putString("fileUri", value.toString())
                } else {
                    remove("fileUri")
                }
            }
        }

    var status: Status
        get() = sharedPreferences.getString("status", null)
            ?.let { enumValueOf<Status>(it) } ?: Status.NOT_STARTED
        set(value) {
            sharedPreferences.edit {
                putString("status", value.name)
            }
        }

    var fetchDownloadingId: Int?
        get() = sharedPreferences.getInt("fetchDownloadingId", -1).takeIf { it != -1 }
        set(value) {
            sharedPreferences.edit {
                if (value != null) {
                    putInt("fetchDownloadingId", value)
                } else {
                    remove("fetchDownloadingId")
                }
            }
        }
}