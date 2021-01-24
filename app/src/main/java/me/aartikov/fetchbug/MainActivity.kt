package me.aartikov.fetchbug

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.Downloader

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_SELECT_DIRECTORY = 200
        const val FILE_NAME = "818"
        const val HTTP_URL = "http://aerostats.getmobileup.com/music/$FILE_NAME.mp3"
    }

    private val fetch: Fetch by lazy {
        val configuration = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(5)
            .setAutoRetryMaxAttempts(3)
            .enableRetryOnNetworkGain(true)
            .enableLogging(true)
            .setHttpDownloader(HttpUrlConnectionDownloader(Downloader.FileDownloaderType.PARALLEL))
            .setNotificationManager(object : DefaultFetchNotificationManager(this) {
                override fun getFetchInstanceForNamespace(namespace: String): Fetch {
                    return fetch
                }
            })
            .build()

        Fetch.getInstance(configuration).apply {
            addListener(DownloadListener())
        }
    }

    private val pref: Pref by lazy { Pref(this) }

    private val directoryUri: TextView by lazy { findViewById(R.id.directoryUri) }
    private val fileUri: TextView by lazy { findViewById(R.id.fileUri) }
    private val status: TextView by lazy { findViewById(R.id.status) }
    private val actionButton: Button by lazy { findViewById(R.id.actionButton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fetch.resumeAll()
        updateView()
    }

    @SuppressLint("SetTextI18n")
    private fun updateView() {
        directoryUri.text = pref.directoryUri?.toString() ?: "<Directory uri is unknown>"
        fileUri.text = pref.fileUri?.toString() ?: "<File uri is unknown>"
        status.text = "Status: ${pref.status.name}"

        when {
            pref.directoryUri == null -> {
                actionButton.text = "Select directory"
                actionButton.setOnClickListener { selectDirectory() }
            }

            pref.status == Status.NOT_STARTED -> {
                actionButton.text = "Download"
                actionButton.setOnClickListener {
                    startDownloading()
                }
            }

            pref.status == Status.DOWNLOADING -> {
                actionButton.text = "Cancel"
                actionButton.setOnClickListener {
                    cancelDownloading()
                }
            }

            pref.status == Status.DOWNLOADED -> {
                actionButton.text = "Remove"
                actionButton.setOnClickListener {
                    removeFile()
                }
            }

            else -> {
                actionButton.text = "Unknown state"
                actionButton.setOnClickListener { }
            }
        }
    }

    private fun selectDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, REQUEST_SELECT_DIRECTORY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SELECT_DIRECTORY && resultCode == RESULT_OK && data?.data != null) {
            onDirectorySelected(data.data!!)
        }
    }

    private fun onDirectorySelected(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, flags)
        pref.directoryUri = uri
        updateView()
    }

    private fun startDownloading() {
        val fileUri = DocumentFile.fromTreeUri(this, pref.directoryUri!!)!!
            .createFile("audio/mpeg", FILE_NAME)!!.uri
        val request = Request(HTTP_URL, fileUri)
        request.enqueueAction = EnqueueAction.UPDATE_ACCORDINGLY

        fetch.enqueue(
            request,
            null,
            { error: Error ->
                Log.d("FetchTest", "Failed to equeue request")
                error.throwable?.printStackTrace()
                Toast.makeText(this, "Failed to equeue request", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun cancelDownloading() {
        pref.fetchDownloadingId?.let {
            fetch.cancel(it)
        }
        pref.fileUri?.let {
            DocumentFile.fromSingleUri(this, it)!!.delete()
        }
        pref.fetchDownloadingId = null
        pref.fileUri = null
        pref.status = Status.NOT_STARTED
        updateView()
    }

    private fun removeFile() {
        pref.fileUri?.let {
            DocumentFile.fromSingleUri(this, it)!!.delete()
        }
        pref.fileUri = null
        pref.status = Status.NOT_STARTED
        updateView()
    }

    inner class DownloadListener() : AbstractFetchListener() {

        override fun onAdded(download: Download) {
            pref.fetchDownloadingId = download.id
            pref.fileUri = download.fileUri
            pref.status = Status.DOWNLOADING
            updateView()
        }

        override fun onCompleted(download: Download) {
            fetch.remove(download.id)   // Fixes other bug
            pref.fetchDownloadingId = null
            pref.status = Status.DOWNLOADED
            updateView()
        }

        override fun onError(download: Download, error: Error, throwable: Throwable?) {
            Log.d("FetchTest", "Failed to download file")
            throwable?.printStackTrace()
            Toast.makeText(this@MainActivity, "Failed to download file", Toast.LENGTH_SHORT).show()

            pref.fetchDownloadingId = null
            pref.fileUri = null
            pref.status = Status.NOT_STARTED
            updateView()
        }

        override fun onCancelled(download: Download) {
            pref.fileUri?.let {
                DocumentFile.fromSingleUri(this@MainActivity, it)!!.delete()
            }
            pref.fetchDownloadingId = null
            pref.fileUri = null
            pref.status = Status.NOT_STARTED
            updateView()
        }
    }
}