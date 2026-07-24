package com.family.pswdmngr.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.data.CardType
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.screens.EmptyState
import com.family.pswdmngr.ui.screens.IconBadge
import com.family.pswdmngr.ui.screens.SectionLabel
import com.family.pswdmngr.ui.theme.*

/** CSD is deliberately separated from personal bank cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsdCardsScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val all by VaultSession.cardDao().observeAll().collectAsState(initial = emptyList())
    val cards = all.filter { it.cardType == CardType.CSD }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("CSD Cards", color = TextPrimary) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("csdEdit/-1") }, containerColor = Amber, shape = CircleShape) {
                Icon(Icons.Rounded.Add, "Add CSD card", tint = Midnight)
            }
        },
    ) { pad ->
        if (cards.isEmpty()) {
            EmptyState(
                Icons.Rounded.ShoppingBag, Amber,
                "No CSD card saved yet",
                "CSD details stay separate from your debit and credit cards.",
                modifier = Modifier.padding(pad),
            )
        } else LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SectionLabel("CANTEEN SMART CARDS") }
            items(cards.size, key = { cards[it].id }) { i ->
                CardFace(cards[i], modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .clickable { nav.navigate("cardDetail/${cards[i].id}") })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
