package com.bqsqwad.proedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bqsqwad.proedit.editor.BqSuperpowerFxCatalog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BqProEditApp() }
    }
}

@Composable
private fun BqProEditApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("BQ Pro Edit", style = MaterialTheme.typography.headlineMedium)
                Text("Professional video editing + BQ Superpower FX", modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text("Import") }
                    Button(onClick = {}) { Text("Projects") }
                    Button(onClick = {}) { Text("Export") }
                }
                Text("Superpower FX", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BqSuperpowerFxCatalog.effects) { effect ->
                        Text("• ${effect.name}: ${effect.description}")
                    }
                }
            }
        }
    }
}
