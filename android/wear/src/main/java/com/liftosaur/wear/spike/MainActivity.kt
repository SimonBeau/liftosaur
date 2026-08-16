package com.liftosaur.wear.spike

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SpikeScreen() } }
    }
}

class SpikeViewModel(application: Application) : AndroidViewModel(application) {
    private val runner = SpikeRunner(application)
    var report by mutableStateOf<SpikeReport?>(null)
        private set
    var running by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            report = runner.inspectRuntimeAndRestore()
            running = false
        }
    }

    fun runProof() {
        if (running) return
        running = true
        viewModelScope.launch {
            report = runner.runProof()
            running = false
        }
    }

    override fun onCleared() {
        runner.close()
    }
}

@Composable
private fun SpikeScreen(model: SpikeViewModel = viewModel()) {
    val report = model.report
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Liftosaur JS Spike", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(if (model.running) "Running…" else "Runtime: ${report?.runtime ?: "checking"}", textAlign = TextAlign.Center)
        report?.let {
            Text(it.bundle, textAlign = TextAlign.Center)
            Text(it.persistence, textAlign = TextAlign.Center)
            it.details.forEach { detail -> Text(detail, textAlign = TextAlign.Center) }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = model::runProof,
            enabled = !model.running && report?.runtime != "UNSUPPORTED",
            modifier = Modifier.fillMaxWidth(0.75f),
        ) {
            Text("Run proof")
        }
    }
}
