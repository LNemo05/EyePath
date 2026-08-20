package org.walkguard.app.ui.apps

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.walkguard.app.R
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.data.db.AppPolicyRepository
import org.walkguard.app.ui.components.AppListDivider
import org.walkguard.app.ui.components.FooterNote
import org.walkguard.app.ui.components.PillChip
import org.walkguard.app.ui.components.PolicyDropdown
import org.walkguard.app.ui.components.ScreenContentPadding
import org.walkguard.app.ui.components.ScreenSubtitle
import org.walkguard.app.ui.components.SearchField
import org.walkguard.app.ui.i18n.appPolicyLabel
import org.walkguard.app.ui.theme.AppleBlue
import org.walkguard.app.ui.theme.AppleCard
import org.walkguard.app.ui.theme.AppleFill
import org.walkguard.app.ui.theme.AppleRed
import org.walkguard.app.ui.theme.AppleSecondary
import org.walkguard.app.ui.theme.AppleText


@Composable
fun AppPoliciesScreen(
    context: Context,
    appPolicyRepository: AppPolicyRepository,
    catalogRepository: AppPolicyCatalogRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val unknownError = stringResource(R.string.value_unknown)
    val savedPolicies = appPolicyRepository.observePolicies().collectAsState(initial = emptyList()).value
    val selfPackageName = context.packageName
    var systemEntries by remember(catalogRepository) { mutableStateOf(emptyList<AppPolicyEntry>()) }
    var hasLoadedCatalog by remember(catalogRepository) { mutableStateOf(false) }
    var isLoading by remember(catalogRepository) { mutableStateOf(true) }
    var loadError by remember(catalogRepository) { mutableStateOf<String?>(null) }
    var loadRequest by remember(catalogRepository) { mutableStateOf(0) }

    val catalog by remember(systemEntries, savedPolicies, selfPackageName) {
        derivedStateOf {
            mergeAppPolicyCatalog(
                systemEntries = systemEntries,
                selfPackageName = selfPackageName,
                savedPolicies = savedPolicies
            )
        }
    }
    val policyByPackage = remember(savedPolicies) { savedPolicies.associateBy { it.packageName } }
    var searchQuery by remember { mutableStateOf("") }
    var policyFilter by remember {
        mutableStateOf<AppPolicyListFilter>(AppPolicyListFilter.All)
    }
    val visibleApps by remember(catalog, searchQuery, policyByPackage, policyFilter) {
        derivedStateOf {
            filterAppPolicyEntries(
                entries = catalog,
                query = searchQuery,
                policyByPackage = policyByPackage,
                policyFilter = policyFilter
            )
        }
    }
    var policyErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loadRequest, catalogRepository) {
        isLoading = true
        loadError = null
        try {
            systemEntries = catalogRepository.load(forceRefresh = loadRequest > 0)
            hasLoadedCatalog = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadError = error.message ?: unknownError
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ScreenContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { loadRequest += 1 },
                enabled = !isLoading
            ) {
                Text(
                    text = stringResource(R.string.apps_refresh),
                    color = if (isLoading) AppleSecondary else AppleBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = stringResource(R.string.apps_title),
                color = AppleText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            // Balance the leading refresh button width for title centering.
            Text(
                text = stringResource(R.string.apps_refresh),
                color = androidx.compose.ui.graphics.Color.Transparent,
                fontSize = 16.sp
            )
        }

        ScreenSubtitle(
            text = stringResource(R.string.apps_subtitle),
            modifier = Modifier.padding(top = 0.dp)
        )

        policyErrorMessage?.let {
            Text(
                text = stringResource(R.string.apps_last_change_failed, it),
                color = AppleRed,
                fontSize = 13.sp
            )
        }

        SearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = stringResource(R.string.apps_search_hint)
        )

        if (searchQuery.isNotEmpty()) {
            TextButton(onClick = { searchQuery = "" }) {
                Text(stringResource(R.string.apps_search_clear), color = AppleBlue)
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                count = 1 + AppPolicy.values().size,
                key = { index -> if (index == 0) "all" else AppPolicy.values()[index - 1].name }
            ) { index ->
                if (index == 0) {
                    PillChip(
                        text = stringResource(R.string.apps_filter_all),
                        selected = policyFilter is AppPolicyListFilter.All,
                        onClick = { policyFilter = AppPolicyListFilter.All }
                    )
                } else {
                    val policy = AppPolicy.values()[index - 1]
                    PillChip(
                        text = appPolicyLabel(policy),
                        selected = (policyFilter as? AppPolicyListFilter.ByPolicy)?.policy == policy,
                        onClick = { policyFilter = AppPolicyListFilter.ByPolicy(policy) }
                    )
                }
            }
        }

        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (hasLoadedCatalog) 18.dp else 24.dp),
                    color = AppleBlue,
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(
                        if (hasLoadedCatalog) R.string.apps_refreshing else R.string.apps_loading
                    ),
                    color = AppleSecondary,
                    fontSize = 14.sp
                )
            }
        }

        loadError?.let { error ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.apps_load_failed, error),
                    modifier = Modifier.weight(1f),
                    color = AppleRed,
                    fontSize = 13.sp
                )
                Button(
                    onClick = { loadRequest += 1 },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppleFill,
                        contentColor = AppleText
                    )
                ) {
                    Text(stringResource(R.string.apps_retry))
                }
            }
        }

        if (visibleApps.isEmpty()) {
            val emptyMessage = when {
                policyFilter !is AppPolicyListFilter.All -> R.string.apps_empty_filter
                searchQuery.isNotBlank() -> R.string.apps_empty_search
                loadError == null && !isLoading -> R.string.apps_empty_catalog
                else -> null
            }
            emptyMessage?.let { message ->
                Text(
                    text = stringResource(message),
                    color = AppleSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            // Flat list surface (no shadow/elevation) so scrolling avoids continuous shadow compositing.
            val listShape = remember { RoundedCornerShape(20.dp) }
            val policyOptions = remember { AppPolicy.entries.toList() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(listShape)
                    .background(AppleCard)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                        val currentPolicy =
                            policyByPackage[app.packageName]?.policy?.toAppPolicyOrNull() ?: AppPolicy.INHERIT
                        if (index > 0) {
                            AppListDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                        AppPolicyRow(
                            app = app,
                            policy = currentPolicy,
                            policyOptions = policyOptions,
                            onPolicySelected = { policy ->
                                scope.launch {
                                    try {
                                        appPolicyRepository.setPolicy(
                                            packageName = app.packageName,
                                            label = app.label,
                                            policy = policy,
                                            updatedAtEpochMs = System.currentTimeMillis()
                                        )
                                        policyErrorMessage = null
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        policyErrorMessage = error.message ?: unknownError
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        FooterNote(stringResource(R.string.apps_count, visibleApps.size))
    }
}

@Composable
private fun AppPolicyRow(
    app: AppPolicyEntry,
    policy: AppPolicy,
    policyOptions: List<AppPolicy>,
    onPolicySelected: (AppPolicy) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = AppleText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = app.packageName,
                color = AppleSecondary,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        PolicyDropdown(
            selected = policy,
            options = policyOptions,
            labelFor = { appPolicyLabel(it) },
            onSelected = onPolicySelected
        )
    }
}

private fun String?.toAppPolicyOrNull(): AppPolicy? {
    return this?.let { runCatching { AppPolicy.valueOf(it) }.getOrNull() }
}
