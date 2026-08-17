package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isDark = LocalIsDarkTheme.current
    val bgGradient = if (isDark) {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "تحلیل بهره‌وری",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "درک الگوهای کاری شما",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            item {
                PeriodSelector(
                    selected = state.period,
                    onSelect = { viewModel.setPeriod(it) }
                )
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
            } else {
                item {
                    state.mostProductiveHourRange?.let { range ->
                        InsightCard(
                            text = "بیشتر کارهایتان را بین ساعت $range تکمیل می‌کنید."
                        )
                    }
                    if (state.tasksScheduled > 0 && state.tasksPostponed > state.tasksScheduled / 3) {
                        Spacer(modifier = Modifier.height(8.dp))
                        InsightCard(
                            text = "این دوره ${state.tasksPostponed} کار را به‌تعویق انداختید. شاید زمان‌بندی واقع‌بینانه‌تری کمک کند."
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CheckCircle,
                            label = "تکمیل‌شده",
                            value = "${state.tasksCompleted}",
                            subtitle = "از ${state.tasksScheduled} کار",
                            accentColor = AccentGreen
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Insights,
                            label = "درصد تکمیل",
                            value = "${state.completionPercentage}٪",
                            subtitle = "${state.tasksPostponed} کار به‌تعویق افتاده",
                            accentColor = AccentBlue
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Timer,
                            label = "زمان فوکوس",
                            value = formatMinutes(state.focusMinutes),
                            subtitle = "${state.completedFocusSessions} جلسه کامل",
                            accentColor = AccentAmber
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Repeat,
                            label = "روتین‌ها",
                            value = if (state.routineInstancesTotal == 0) "—" else
                                "${state.routineInstancesCompleted}/${state.routineInstancesTotal}",
                            subtitle = "روتین تکمیل‌شده",
                            accentColor = AccentTeal
                        )
                    }
                }

                if (state.habitConsistency.isNotEmpty()) {
                    item {
                        SectionHeader(icon = Icons.Default.FavoriteBorder, title = "ثبات عادت‌ها")
                    }
                    items(state.habitConsistency, key = { it.habit.id }) { hc ->
                        HabitConsistencyRow(hc)
                    }
                }

                if (state.goalProgress.isNotEmpty()) {
                    item {
                        SectionHeader(icon = Icons.Default.Flag, title = "پیشرفت اهداف")
                    }
                    items(state.goalProgress, key = { it.goal.id }) { gp ->
                        GoalProgressRow(gp)
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PeriodSelector(selected: AnalyticsPeriod, onSelect: (AnalyticsPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == AnalyticsPeriod.WEEK,
            onClick = { onSelect(AnalyticsPeriod.WEEK) },
            label = { Text("این هفته") },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = selected == AnalyticsPeriod.MONTH,
            onClick = { onSelect(AnalyticsPeriod.MONTH) },
            label = { Text("این ماه") },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
}

@Composable
private fun InsightCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Insights, contentDescription = null, tint = AccentAmber)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = modifier
            .glassCard()
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accentColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun HabitConsistencyRow(hc: HabitConsistency) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(14.dp)
            // Prompt section 53 (Accessibility): without this, TalkBack reads
            // the habit name, the "X% (Y/Z)" text, and the progress bar's own
            // auto-generated percentage as three disconnected announcements.
            // Merging them ties the number to what it's actually progress of.
            .semantics(mergeDescendants = true) {
                contentDescription = "عادت ${hc.habit.name}: ${hc.percentage} درصد تکمیل، ${hc.completedDays} از ${hc.totalDays} روز"
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = hc.habit.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${hc.percentage}٪ (${hc.completedDays}/${hc.totalDays})",
                color = AccentGreen,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { hc.percentage / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = AccentGreen,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun GoalProgressRow(gp: GoalProgressSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "هدف ${gp.goal.title}: ${gp.goal.progressPercentage} درصد تکمیل"
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = gp.goal.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${gp.goal.progressPercentage}٪",
                color = AccentBlue,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { gp.goal.progressPercentage / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = AccentBlue,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}س ${minutes}د" else "${minutes}د"
}
