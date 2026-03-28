package com.arcanys.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcanys.hush.data.SessionEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FocusSessionScreen(sessions: List<SessionEntity>) {
    val groupedSessions = groupSessionsByDate(sessions)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            groupedSessions.forEach { (date, dailySessions) ->
                item {
                    DateHeader(
                        date = date,
                        totalTime = calculateTotalTime(dailySessions)
                    )
                }
                items(dailySessions) { session ->
                    SessionItem(session)
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: String, totalTime: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "${totalTime}m total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun SessionItem(session: SessionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.blockName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${session.duration}m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun groupSessionsByDate(sessions: List<SessionEntity>): Map<String, List<SessionEntity>> {
    val dateFormatter = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    return sessions.groupBy { session ->
        dateFormatter.format(Date(session.timestamp))
    }
}

private fun calculateTotalTime(sessions: List<SessionEntity>): Int {
    return sessions.sumOf { it.duration }
}
