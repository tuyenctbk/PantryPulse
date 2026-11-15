package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiFeatureType
import com.example.ai.UsageQuotaSnapshot
import com.example.data.ChatMessage
import com.example.data.KitchenSetupState
import com.example.data.PantryItemEntity
import com.example.ui.ChatUiState
import com.example.ui.components.QuotaSoftNudgeBanner
import com.example.ui.components.SmartQuotaRecoveryCard
import java.util.Locale

@Composable
fun SousChefChatScreen(
    messages: List<ChatMessage>,
    chatUiState: ChatUiState,
    quotaState: UsageQuotaSnapshot,
    pantryItems: List<PantryItemEntity>,
    kitchenSetup: KitchenSetupState,
    onSendMessage: (String) -> Unit,
    onEnableEcoLite: () -> Unit,
    onClaimGraceCredits: () -> Unit,
    onRetryEcoLite: () -> Unit,
    onUseLocalFallback: (String) -> Unit,
    onOpenValueReceipt: () -> Unit,
    onClearChat: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    var inputText by rememberSaveable { mutableStateOf("") }
    var autoSpeakReplies by rememberSaveable { mutableStateOf(false) }
    var lastSpokenMessageId by rememberSaveable { mutableStateOf("") }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.US
            }
        }
        ttsEngine = instance
        onDispose {
            instance?.stop()
            instance?.shutdown()
        }
    }

    val listState = rememberLazyListState()

    val lastMessage = messages.lastOrNull()
    val lastMessageText = lastMessage?.text.orEmpty()
    LaunchedEffect(messages.size, lastMessageText) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Automatically read completed Sous-Chef replies aloud when Hands-Free Voice Mode is active
    LaunchedEffect(lastMessage?.id, lastMessage?.isStreaming, autoSpeakReplies) {
        if (autoSpeakReplies &&
            lastMessage != null &&
            !lastMessage.isUser &&
            !lastMessage.isStreaming &&
            lastMessage.id != lastSpokenMessageId &&
            messages.size > 1
        ) {
            lastSpokenMessageId = lastMessage.id
            val cleanSpeech = lastMessage.text
                .replace("**", "")
                .replace("*", "")
                .replace("#", "")
            ttsEngine?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "chef_reply_${lastMessage.id}")
        }
    }

    val quickPrompts = remember(pantryItems, kitchenSetup) {
        val topExpiring = pantryItems.firstOrNull { it.daysUntilExpiry <= 2 }?.name ?: "wilting greens"
        val topAppliance = kitchenSetup.ownedAppliances.firstOrNull() ?: "Skillet"
        listOf(
            "1:1 Substitute for heavy cream or eggs using my fridge?",
            "Spoilage Safety Fact-Check: Is $topExpiring safe to cook?",
            "Quick 10-min sauce using $topExpiring?",
            "How do I crisp leftovers in my $topAppliance?",
            "What can I cook tonight with zero grocery shopping?",
            "How to revive slightly wilted vegetables?"
        )
    }

    val voicePromptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                onSendMessage(spoken)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sous_chef_chat_screen")
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.chat_title, quotaState.activeCapabilityTier.badgeLabel),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(id = R.string.chat_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = autoSpeakReplies,
                            onClick = {
                                autoSpeakReplies = !autoSpeakReplies
                                if (!autoSpeakReplies) {
                                    ttsEngine?.stop()
                                }
                            },
                            label = {
                                Text(
                                    if (autoSpeakReplies) {
                                        stringResource(id = R.string.chat_hands_free_on)
                                    } else {
                                        stringResource(id = R.string.chat_hands_free_off)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (autoSpeakReplies) {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    },
                                    contentDescription = "Toggle Hands-Free Spoken Replies",
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            modifier = Modifier.testTag("chef_chat_tts_toggle")
                        )

                        if (messages.size > 1 && chatUiState !is ChatUiState.Streaming) {
                            TextButton(
                                onClick = {
                                    ttsEngine?.stop()
                                    onClearChat()
                                },
                                modifier = Modifier.testTag("reset_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Reset chat",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPrompts.forEachIndexed { idx, prompt ->
                        AssistChip(
                            onClick = { onSendMessage(prompt) },
                            label = { Text(prompt, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.testTag("quick_prompt_chip_$idx")
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (quotaState.isSoftNudgeThreshold && !quotaState.ecoLiteMode) {
                item {
                    QuotaSoftNudgeBanner(
                        quota = quotaState,
                        onEnableEcoLite = onEnableEcoLite,
                        onOpenValueReceipt = onOpenValueReceipt
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatBubbleRow(
                    message = msg,
                    onSpeakMessage = { textToRead ->
                        val cleanSpeech = textToRead.replace("**", "").replace("*", "")
                        ttsEngine?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "bubble_${msg.id}")
                    }
                )
            }

            if (chatUiState is ChatUiState.QuotaRecoveryNeeded || quotaState.isExhausted) {
                item {
                    SmartQuotaRecoveryCard(
                        feature = AiFeatureType.SOUS_CHEF_CHAT,
                        quota = quotaState,
                        onClaimGraceCredits = onClaimGraceCredits,
                        onRetryEcoLite = onRetryEcoLite,
                        onUseLocalFallback = { onUseLocalFallback(inputText) },
                        onOpenValueReceipt = onOpenValueReceipt
                    )
                }
            }

            if (chatUiState is ChatUiState.Error) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_error_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = chatUiState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(
                                onClick = { onUseLocalFallback(inputText) }
                            ) {
                                Text("Reply with Local Reflex Chef (0 Cloud Credits)")
                            }
                        }
                    }
                }
            }
        }

        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(
                                    RecognizerIntent.EXTRA_PROMPT,
                                    "Ask Sous-Chef a cooking, substitution, or spoilage question..."
                                )
                            }
                            voicePromptLauncher.launch(intent)
                        } catch (_: Exception) {
                            // Emulator fallback when no hardware mic / voice activity is present
                            inputText = quickPrompts.first()
                        }
                    },
                    enabled = chatUiState !is ChatUiState.Streaming,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chef_chat_voice_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Ask Sous-Chef by Voice",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chef_chat_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val toSend = inputText
                            inputText = ""
                            onSendMessage(toSend)
                        }
                    },
                    enabled = inputText.isNotBlank() && chatUiState !is ChatUiState.Streaming,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chef_chat_send_button")
                ) {
                    if (chatUiState is ChatUiState.Streaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send to Sous-Chef"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(
    message: ChatMessage,
    onSpeakMessage: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!message.isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (message.isStreaming) "Sous-Chef • typing..." else "Sous-Chef",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!message.isStreaming && message.text.isNotBlank()) {
                            IconButton(
                                onClick = { onSpeakMessage(message.text) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Read response aloud",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = message.text.ifBlank { "..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
