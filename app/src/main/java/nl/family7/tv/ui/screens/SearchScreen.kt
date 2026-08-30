package nl.family7.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.family7.tv.data.Family7CatalogRepository
import nl.family7.tv.data.ProgramItem
import nl.family7.tv.ui.components.TVButton
import nl.family7.tv.ui.components.TVProgramCard
import nl.family7.tv.ui.theme.Family7BlueDark
import nl.family7.tv.ui.theme.Family7Orange
import nl.family7.tv.ui.theme.TextPrimary
import nl.family7.tv.ui.theme.TextSecondary

@Composable
fun SearchScreen(
    catalogRepo: Family7CatalogRepository,
    onSelectProgram: (ProgramItem) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Alles") }
    var allPrograms by remember { mutableStateOf<List<ProgramItem>>(emptyList()) }
    var displayedResults by remember { mutableStateOf<List<ProgramItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val quickFilters = listOf(
        "Alles", "A-Z", "0-9", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    // Load entire dynamic live catalog from Family7
    LaunchedEffect(Unit) {
        isLoading = true
        val res = catalogRepo.getAllAZPrograms()
        res.onSuccess {
            allPrograms = it
            displayedResults = it
            isLoading = false
        }.onFailure {
            isLoading = false
        }
    }

    // Filter computation
    fun applyFilter(filter: String, query: String) {
        var list = allPrograms

        // Apply text query if present
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) || it.slug.lowercase().contains(q)
            }
        }

        // Apply quick category or letter filter
        list = when (filter) {
            "Alles" -> list
            "0-9" -> list.filter { it.title.firstOrNull()?.isDigit() == true }
            "A-Z" -> list.sortedBy { it.title.lowercase() }
            else -> {
                if (filter.length == 1 && filter[0].isLetter()) {
                    list.filter { it.title.startsWith(filter, ignoreCase = true) }
                } else {
                    list
                }
            }
        }

        displayedResults = list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Family7BlueDark)
            .padding(28.dp)
    ) {
        // Top Row: Back button & Search Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TVButton(
                text = "TERUG",
                onClick = onBack,
                isPrimary = false,
                leadingIcon = {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
            )

            Spacer(modifier = Modifier.width(20.dp))

            TVInputField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    applyFilter(selectedFilter, it)
                },
                placeholder = "Zoek op titel, thema of trefwoord...",
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Family7Orange)
                },
                onImeAction = { applyFilter(selectedFilter, searchQuery) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Filters & Alphabet Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickFilters) { filter ->
                val isSelected = selectedFilter == filter
                TVButton(
                    text = filter,
                    onClick = {
                        selectedFilter = filter
                        applyFilter(filter, searchQuery)
                    },
                    isPrimary = isSelected
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Results Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isLoading) "Programma's laden..." else "${displayedResults.size} programma's gevonden",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "Zoekresultaten voor \"$searchQuery\"",
                    color = Family7Orange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Results Grid
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Family7Orange)
            }
        } else if (displayedResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Geen programma's gevonden voor \"$searchQuery\".",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedResults, key = { it.slug }) { item ->
                    TVProgramCard(
                        item = item,
                        onClick = { onSelectProgram(item) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
