package nl.family7.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.family7.tv.data.CategoryRow
import nl.family7.tv.data.Family7CatalogRepository
import nl.family7.tv.data.ProgramItem
import nl.family7.tv.ui.components.TVButton
import nl.family7.tv.ui.components.TVProgramCard
import nl.family7.tv.ui.theme.DarkSurface
import nl.family7.tv.ui.theme.Family7BlueDark
import nl.family7.tv.ui.theme.Family7Orange
import nl.family7.tv.ui.theme.TextPrimary
import nl.family7.tv.ui.theme.TextSecondary

enum class NavDestination {
    ON_DEMAND, LIVE_TV, ORIGINALS, AZ, SEARCH, LOGOUT
}

@Composable
fun HomeScreen(
    catalogRepo: Family7CatalogRepository,
    onNavigateToLive: () -> Unit,
    onSelectProgram: (ProgramItem) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAZ: () -> Unit,
    onLogout: () -> Unit
) {
    var categoryRows by remember { mutableStateOf<List<CategoryRow>>(emptyList()) }
    var featuredProgram by remember { mutableStateOf<ProgramItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedNav by remember { mutableStateOf(NavDestination.ON_DEMAND) }

    LaunchedEffect(Unit) {
        isLoading = true
        val res = catalogRepo.getOnDemandHome()
        res.onSuccess { rows ->
            categoryRows = rows
            val firstRow = rows.firstOrNull()
            if (firstRow != null && firstRow.items.isNotEmpty()) {
                featuredProgram = firstRow.items.first()
            }
            isLoading = false
        }.onFailure {
            errorMessage = it.message
            isLoading = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Family7BlueDark)
    ) {
        // Left Side Navigation Bar
        TVSideNav(
            selected = selectedNav,
            onSelect = { nav ->
                selectedNav = nav
                when (nav) {
                    NavDestination.LIVE_TV -> onNavigateToLive()
                    NavDestination.SEARCH -> onNavigateToSearch()
                    NavDestination.AZ -> onNavigateToAZ()
                    NavDestination.LOGOUT -> onLogout()
                    else -> {}
                }
            }
        )

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Family7Orange)
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kon catalogus niet laden: $errorMessage", color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        TVButton(text = "OPNIEUW PROBEREN", onClick = {
                            isLoading = true
                            errorMessage = null
                        })
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    // Featured Banner
                    item {
                        if (featuredProgram != null) {
                            HeroBanner(
                                program = featuredProgram!!,
                                onWatchClick = { onSelectProgram(featuredProgram!!) },
                                onLiveClick = onNavigateToLive
                            )
                        }
                    }

                    // Category Rows
                    items(categoryRows) { row ->
                        CategorySwimlane(
                            row = row,
                            onProgramClick = onSelectProgram
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TVSideNav(
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .fillMaxSize()
            .background(DarkSurface.copy(alpha = 0.95f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo / Emblem Top
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Family7Orange),
            contentAlignment = Alignment.Center
        ) {
            Text("7", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }

        // Navigation Items
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TVSideNavItem(
                icon = Icons.Default.Movie,
                label = "On Demand",
                isSelected = selected == NavDestination.ON_DEMAND,
                onClick = { onSelect(NavDestination.ON_DEMAND) }
            )
            TVSideNavItem(
                icon = Icons.Default.LiveTv,
                label = "Live TV",
                isSelected = selected == NavDestination.LIVE_TV,
                onClick = { onSelect(NavDestination.LIVE_TV) }
            )
            TVSideNavItem(
                icon = Icons.Default.SortByAlpha,
                label = "A-Z",
                isSelected = selected == NavDestination.AZ,
                onClick = { onSelect(NavDestination.AZ) }
            )
            TVSideNavItem(
                icon = Icons.Default.Search,
                label = "Zoeken",
                isSelected = selected == NavDestination.SEARCH,
                onClick = { onSelect(NavDestination.SEARCH) }
            )
        }

        // Logout Bottom
        TVSideNavItem(
            icon = Icons.Default.ExitToApp,
            label = "Uitloggen",
            isSelected = selected == NavDestination.LOGOUT,
            onClick = { onSelect(NavDestination.LOGOUT) }
        )
    }
}

@Composable
fun TVSideNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(if (isFocused) 1.15f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isFocused -> Family7Orange
                    isSelected -> Color(0xFF03326C)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused || isSelected) Color.White else TextSecondary
        )
    }
}

@Composable
fun HeroBanner(
    program: ProgramItem,
    onWatchClick: () -> Unit,
    onLiveClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Backdrop Image
        AsyncImage(
            model = program.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Family7BlueDark.copy(alpha = 0.95f),
                            Family7BlueDark.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Family7BlueDark
                        ),
                        startY = 140f
                    )
                )
        )

        // Details
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 36.dp, end = 48.dp)
        ) {
            if (program.badge.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Family7Orange)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = program.badge.uppercase(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = program.title,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TVButton(
                    text = "BEKIJKEN",
                    onClick = onWatchClick,
                    leadingIcon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                )
                TVButton(
                    text = "LIVE TV",
                    onClick = onLiveClick,
                    isPrimary = false,
                    leadingIcon = {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White)
                    }
                )
            }
        }
    }
}

@Composable
fun CategorySwimlane(
    row: CategoryRow,
    onProgramClick: (ProgramItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = row.title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(row.items) { item ->
                TVProgramCard(
                    item = item,
                    onClick = { onProgramClick(item) }
                )
            }
        }
    }
}
