package org.walkguard.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.walkguard.app.R
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.data.db.StatsDao
import org.walkguard.app.ui.components.AppleCard
import org.walkguard.app.ui.components.AppListDivider
import org.walkguard.app.ui.components.CardTitle
import org.walkguard.app.ui.components.LargeTitle
import org.walkguard.app.ui.components.MetricBarRow
import org.walkguard.app.ui.components.ScreenContentPadding
import org.walkguard.app.ui.components.ScreenSubtitle
import org.walkguard.app.ui.components.SecondaryValue
import org.walkguard.app.ui.i18n.guardModeLabel
import org.walkguard.app.ui.theme.AppleBlue
import org.walkguard.app.ui.theme.AppleOrange
import org.walkguard.app.ui.theme.AppleRed
import org.walkguard.app.ui.theme.AppleSecondary
import org.walkguard.app.ui.theme.AppleText
import java.time.LocalDate

@Composable
fun StatsScreen(
    statsDao: StatsDao,
    modifier: Modifier = Modifier
) {
    // Recompute the local day periodically so midnight rollover does not leave a stale snapshot.
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        while (true) {
            day = LocalDate.now().toString()
            delay(60_000L)
        }
    }

    val dailyStats by statsDao.observeDailyStats(day).collectAsState(initial = null)
    val topApps by statsDao.observeTopAppDailyStats(day, 10).collectAsState(initial = emptyList())

    val mild = dailyStats?.mildCount ?: 0
    val normal = dailyStats?.normalCount ?: 0
    val rage = dailyStats?.rageCount ?: 0
    val total = mild + normal + rage
    val maxMode = maxOf(mild, normal, rage, 1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LargeTitle(stringResource(R.string.stats_title))
        ScreenSubtitle(stringResource(R.string.stats_today, day))

        AppleCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.stats_interventions),
                    color = AppleSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = total.toString(),
                    color = AppleText,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    lineHeight = 50.sp
                )
                Text(
                    text = stringResource(R.string.stats_effective_interventions),
                    color = AppleSecondary,
                    fontSize = 14.sp
                )
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBarRow(
                        label = guardModeLabel(GuardMode.MILD),
                        value = mild,
                        maxValue = maxMode,
                        barColor = AppleBlue
                    )
                    MetricBarRow(
                        label = guardModeLabel(GuardMode.NORMAL),
                        value = normal,
                        maxValue = maxMode,
                        barColor = AppleOrange
                    )
                    MetricBarRow(
                        label = guardModeLabel(GuardMode.RAGE),
                        value = rage,
                        maxValue = maxMode,
                        barColor = AppleRed
                    )
                }
            }
        }

        AppleCard {
            CardTitle(
                text = stringResource(R.string.stats_top_packages),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)
            )
            if (topApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_no_interceptions),
                    color = AppleSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    topApps.forEachIndexed { index, appStats ->
                        if (index > 0) {
                            AppListDivider()
                        }
                        val percent = if (total <= 0) {
                            0
                        } else {
                            ((appStats.interventionCount * 100f) / total).toInt()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = appStats.packageName,
                                    color = AppleText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(
                                        R.string.stats_times,
                                        appStats.interventionCount
                                    ),
                                    color = AppleSecondary,
                                    fontSize = 12.5.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            SecondaryValue("$percent%")
                        }
                    }
                }
            }
        }
    }
}
