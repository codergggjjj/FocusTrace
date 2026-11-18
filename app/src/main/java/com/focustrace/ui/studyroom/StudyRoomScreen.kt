package com.focustrace.ui.studyroom

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.focustrace.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.focustrace.studyroom.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RoomSearchTime = DateTimeFormatter.ofPattern("HH:mm:ss")
private val RoomNumerals = FontFamily(Font(R.font.barlow_semicondensed_medium, FontWeight.Medium))

private const val SHARE_NOTICE = "同一 Wi-Fi 的用户可直接加入，并看到你的昵称、头像、当前任务、专注状态和今日累计时长。"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyRoomScreen(viewModel: StudyRoomViewModel, onFocus: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val activeFocus by viewModel.activeFocus.collectAsStateWithLifecycle(initialValue = false)
    val starting by viewModel.starting.collectAsStateWithLifecycle()
    val startError by viewModel.startError.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(false) }
    var editProfile by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var joinKey by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val savedRoom by viewModel.savedRoom.collectAsStateWithLifecycle()
    val lastInvite by viewModel.lastInvite.collectAsStateWithLifecycle()
    val invitation by viewModel.invitation.collectAsStateWithLifecycle()
    var enteringInvite by rememberSaveable { mutableStateOf(false) }
    var inviteText by rememberSaveable { mutableStateOf("") }
    var forgetGuest by rememberSaveable { mutableStateOf(false) }
    var forgetRoom by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        if (viewModel.state.value == StudyRoomState.Lobby && viewModel.savedRoom.value != null) viewModel.restoreHost()
    }
    var close by rememberSaveable { mutableStateOf(false) }
    var sharingAccepted by rememberSaveable { mutableStateOf(false) }
    val lobby = state == StudyRoomState.Lobby
    fun openFocus(taskId: Long?) = viewModel.start(taskId) { picking = false; onFocus() }

    when (val room = state) {
        StudyRoomState.Lobby -> {
            val pull = rememberPullToRefreshState()
            val owner = LocalLifecycleOwner.current
            var searchRequest by remember { mutableIntStateOf(0) }
            LaunchedEffect(pull.isRefreshing) {
                if (pull.isRefreshing) searchRequest++
            }
            LaunchedEffect(owner, viewModel, searchRequest) {
                owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.browse { pull.endRefresh() }
                }
            }
            Box(Modifier.fillMaxSize().nestedScroll(pull.nestedScrollConnection)) {
                LazyColumn(Modifier.fillMaxSize().testTag("study-room-lobby"),
                    contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("自习室", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(4.dp))
                        Text("同一 Wi-Fi，一起专注", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(profile, 44)
                                Text(profile.nickname, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { editProfile = true }) { Text("编辑资料") }
                            }
                        }
                    }
                    item {
                        Button(onClick = { if (savedRoom != null) viewModel.restoreHost() else create = true }, enabled = discovery.wifiAvailable,
                            modifier = Modifier.fillMaxWidth().testTag("study-room-create")) { Text(savedRoom?.let { "重新进入「$it」" } ?: "创建自习室") }
                    }
                    item {
                        Row {
                            TextButton(onClick = { inviteText = ""; enteringInvite = true }) { Text("通过邀请加入") }
                            if (lastInvite != null) {
                                TextButton(enabled = discovery.wifiAvailable, onClick = viewModel::returnToRoom) { Text("重返上次房间") }
                                TextButton(onClick = { forgetGuest = true }) { Text("忘记房间") }
                            }
                            if (savedRoom != null) TextButton(onClick = { forgetRoom = true }) { Text("解散我的房间") }
                        }
                    }
                    item {
                        Text("附近的自习室", style = MaterialTheme.typography.titleMedium)
                        val started = remember(discovery.startedAt) { discovery.startedAt?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(RoomSearchTime)
                        } }
                        Text(
                            when {
                                !discovery.wifiAvailable -> "连接 Wi-Fi 后即可发现附近的房间。"
                                discovery.failed -> "暂时无法搜索房间，正在重试。"
                                started == null -> "正在启动搜索…"
                                else -> "${started} 开始搜索 · ${if (discovery.rooms.isEmpty()) "暂未发现房间" else "发现 ${discovery.rooms.size} 个房间"} · 下拉刷新"
                            }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    items(discovery.rooms, key = { it.key }) { listing ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(listing.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { if (sharingAccepted) viewModel.join(listing) else joinKey = listing.key }) { Text("加入") }
                            }
                        }
                    }
                }
                PullToRefreshContainer(state = pull, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
        is StudyRoomState.Connecting -> {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (room.hosting) "正在创建自习室…" else "正在加入自习室…", style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = viewModel::leave) { Text("取消") }
            }
        }
        is StudyRoomState.Joined -> {
            val roster = room.roster
            val displayMembers = remember(roster) { roster.members.sortedWith(compareByDescending<RoomMember> { it.focus.todaySeconds }.thenBy { it.id }) }
            Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().testTag("study-room-joined"),
                contentPadding = PaddingValues(top = 20.dp, bottom = 84.dp)) {
                item {
                    Text(roster.name, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${roster.members.size} / $ROOM_CAPACITY 人在房间", Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (roster.isHost) invitation?.let { link ->
                            TextButton(onClick = {
                                val share = android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, link)
                                context.startActivity(android.content.Intent.createChooser(share, "邀请加入自习室"))
                            }) { Text("邀请") }
                        }
                        TextButton(onClick = { close = true }, modifier = Modifier.testTag("study-room-leave")) {
                            Text(if (roster.isHost) "关闭自习室" else "离开自习室")
                        }
                    }
                    Text("今日专注排行", Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium)
                }
                items(displayMembers, key = { it.id }) { member ->
                    val rank = displayMembers.indexOfFirst { it.focus.todaySeconds == member.focus.todaySeconds } + 1
                    MemberCard(member, rank)
                }
            }
            Button(onClick = { if (activeFocus) onFocus() else picking = true }, enabled = !starting,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("study-room-focus")) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                Text(if (activeFocus) "返回专注" else "开始专注")
            }
            }
        }
    }

    if (forgetGuest && lobby) AlertDialog(onDismissRequest = { forgetGuest = false },
        title = { Text("忘记上次加入的房间？") }, text = { Text("只移除本机保存的邀请，以后仍可通过新邀请加入。") },
        confirmButton = { TextButton(onClick = { viewModel.forgetGuest(); forgetGuest = false }) { Text("忘记") } },
        dismissButton = { TextButton(onClick = { forgetGuest = false }) { Text("取消") } })
    if (forgetRoom && lobby) AlertDialog(onDismissRequest = { forgetRoom = false },
        title = { Text("解散已保存的自习室？") }, text = { Text("旧邀请将失效。") },
        confirmButton = { TextButton(onClick = { viewModel.forgetHost(); forgetRoom = false }) { Text("解散") } },
        dismissButton = { TextButton(onClick = { forgetRoom = false }) { Text("取消") } })
    if (enteringInvite && lobby) {
        val valid = RoomInvite.parse(inviteText) != null
        AlertDialog(onDismissRequest = { enteringInvite = false }, title = { Text("通过邀请加入") },
            text = { Column {
                OutlinedTextField(inviteText, { inviteText = it.take(256) }, singleLine = true,
                    label = { Text("粘贴房主分享的邀请") }, isError = inviteText.isNotBlank() && !valid)
                Text(if (inviteText.isNotBlank() && !valid) "邀请格式不正确，请粘贴完整邀请。" else SHARE_NOTICE)
            } },
            confirmButton = { TextButton(enabled = valid && discovery.wifiAvailable, onClick = {
                enteringInvite = false; viewModel.joinInvite(inviteText)
            }) { Text("加入") } },
            dismissButton = { TextButton(onClick = { enteringInvite = false }) { Text("取消") } })
    }
    if (create && lobby) RoomNameDialog(profile.nickname, onDismiss = { create = false }) { name ->
        sharingAccepted = true; create = false; viewModel.create(name)
    }
    if (editProfile && lobby) ProfileDialog(viewModel, profile) { editProfile = false }
    val pendingRoom = discovery.rooms.firstOrNull { it.key == joinKey }
    if (joinKey != null && lobby) AlertDialog(onDismissRequest = { joinKey = null },
        title = { Text(pendingRoom?.let { "加入「${it.name}」" } ?: "这个自习室已离线") },
        text = { Text(if (pendingRoom != null) SHARE_NOTICE else "可以从附近的房间列表重新选择。") },
        confirmButton = {
            TextButton(onClick = {
                joinKey = null
                if (pendingRoom != null) { sharingAccepted = true; viewModel.join(pendingRoom) }
            }) { Text(if (pendingRoom != null) "加入" else "知道了") }
        }, dismissButton = { if (pendingRoom != null) TextButton(onClick = { joinKey = null }) { Text("取消") } })

    if (close && state !is StudyRoomState.Joined) SideEffect { close = false }
    val isHost = (state as? StudyRoomState.Joined)?.roster?.isHost == true
    if (close && state is StudyRoomState.Joined) AlertDialog(onDismissRequest = { close = false },
        title = { Text(if (isHost) "关闭自习室？" else "离开自习室？") },
        text = { Text(if (isHost) "其他成员会离开房间，所有人的个人计时都会保留。" else "你正在进行的专注会继续。") },
        confirmButton = { TextButton(onClick = { close = false; viewModel.leave() }) { Text(if (isHost) "确认关闭" else "确认离开") } },
        dismissButton = { TextButton(onClick = { close = false }) { Text("取消") } })

    if (picking) {
        val choices by viewModel.choices.collectAsStateWithLifecycle(initialValue = emptyList())
        val minutes by viewModel.defaultMinutes.collectAsStateWithLifecycle(initialValue = 25)
        ModalBottomSheet(onDismissRequest = { if (!starting) picking = false }) {
            LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Text("我的今日待办", Modifier.padding(6.dp), style = MaterialTheme.typography.titleLarge) }
                if (state !is StudyRoomState.Joined) item {
                    Text("房间已断开，你仍然可以开始个人专注。", Modifier.padding(6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                startError?.let { error -> item { Text(error, Modifier.padding(6.dp), color = MaterialTheme.colorScheme.error) } }
                items(choices, key = { it.id }) { task ->
                    FocusChoice(task.title, if (task.timerType == 1) "正向计时" else "番茄钟 · ${task.targetMinutes} 分钟",
                        !starting, "study-room-task-${task.id}") { openFocus(task.id) }
                }
                item { FocusChoice("自由专注", "番茄钟 · $minutes 分钟", !starting, "study-room-free") { openFocus(null) } }
            }
        }
    }
}

@Composable
private fun FocusChoice(title: String, subtitle: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = MaterialTheme.shapes.medium) {
        ListItem(headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(subtitle) }, trailingContent = { Icon(Icons.Outlined.PlayArrow, null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
    }
}

@Composable
private fun RoomNameDialog(nickname: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("${nickname.take(10)}的自习室") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("创建自习室") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(ROOM_NAME_LIMIT) }, singleLine = true, label = { Text("房间名称") },
                modifier = Modifier.fillMaxWidth().testTag("study-room-name"))
            Text(SHARE_NOTICE, style = MaterialTheme.typography.bodyMedium)
        }
    }, confirmButton = { TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("创建并广播") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ProfileDialog(viewModel: StudyRoomViewModel, profile: RoomProfile, onDismiss: () -> Unit) {
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var avatar by rememberSaveable { mutableStateOf(profile.avatar) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            try { avatar = viewModel.importAvatar(uri) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "无法读取这张图片，请换一张。" }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("自习室资料") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(RoomProfile(nickname, avatar), 48)
                TextButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("选择头像") }
                if (avatar.isNotEmpty()) TextButton(onClick = { avatar = "" }, enabled = !busy) { Text("移除") }
            }
            OutlinedTextField(nickname, { nickname = it.take(NICKNAME_LIMIT) }, singleLine = true,
                label = { Text("昵称") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = !busy && nickname.isNotBlank(), onClick = {
            scope.launch {
                busy = true; error = null
                try { viewModel.saveProfile(nickname, avatar); onDismiss() }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { error = "无法保存资料，请重试。" }
                finally { busy = false }
            }
        }) { Text("保存") }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
private fun Avatar(profile: RoomProfile, size: Int) {
    val image by produceState<ImageBitmap?>(null, profile.avatar) {
        value = withContext(Dispatchers.IO) { decodeRoomAvatar(profile.avatar)?.asImageBitmap() }
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)) }
                ?: Text(profile.nickname.take(1), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MemberCard(member: RoomMember, rank: Int) {
    val textInset = 8.dp
    val alignWithTask = Modifier.padding(start = textInset)
    val status = member.focus.status
    val colors = MaterialTheme.colorScheme
    val (tone, symbol) = when (status) {
        RoomStatus.FOCUSING -> Color(0xFF54AA99) to Icons.Outlined.FiberManualRecord
        RoomStatus.PAUSED -> Color(0xFFD3A756) to Icons.Outlined.Pause
        RoomStatus.RESTING -> Color(0xFF969AD1) to Icons.Outlined.Bedtime
        RoomStatus.IDLE -> Color(0xFF8299AE) to Icons.Outlined.RadioButtonUnchecked
        RoomStatus.DISTRACTED -> Color(0xFFBE8996) to Icons.Outlined.Schedule
    }
    val light = colors.surface.luminance() > .5f
    val ink = if (light) Color(0xFF30698A) else Color(0xFFA3D8F4)
    val blush = colors.primary.copy(alpha = if (light) .10f else .16f).compositeOver(colors.surface)
    Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(blush, colors.surface)))
        .heightIn(min = 106.dp).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            val medal = when (rank) {
                1 -> Color(0xFFDEAB42)
                2 -> Color(0xFF96A7BA)
                3 -> Color(0xFFBF9177)
                else -> colors.outline
            }
            Surface(shape = MaterialTheme.shapes.small,
                color = medal.copy(alpha = if (rank <= 3) .32f else .08f).compositeOver(colors.surface)) {
                Text(rank.toString(), Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
            }
        }
        Avatar(member.profile, 44)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(alignWithTask, verticalAlignment = Alignment.CenterVertically) {
                Text(member.profile.nickname, Modifier.weight(1f, fill = false), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface)
                if (member.id == HOST_ID) Text("房主", Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            if (status == RoomStatus.IDLE) {
                Text("今日已专注", alignWithTask, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } else {
                Surface(shape = MaterialTheme.shapes.small,
                    color = if (light) Color(0xFFF0E6FA) else Color(0xFF402F50),
                    contentColor = if (light) Color(0xFF583673) else Color(0xFFEBD5FF)) {
                    Text(member.focus.task ?: "自由专注", Modifier.padding(horizontal = textInset, vertical = 3.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                }
                MemberElapsed(member, alignWithTask)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text((member.focus.todaySeconds / 60).toString(), Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = RoomNumerals, fontSize = 40.sp, letterSpacing = 0.sp,
                        fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium), color = ink)
                Text("分钟", Modifier.alignByBaseline(), style = MaterialTheme.typography.labelSmall, color = ink)
            }
            Surface(shape = CircleShape, color = tone.copy(alpha = .16f).compositeOver(colors.surface)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(symbol, null, Modifier.size(11.dp), tint = tone.copy(alpha = .4f).compositeOver(colors.onSurface))
                    Text(status.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurface)
                }
            }
        }
    }
    HorizontalDivider(color = colors.outlineVariant.copy(alpha = .45f))
}

@Composable
private fun MemberElapsed(member: RoomMember, modifier: Modifier) {
    val seconds = if (!member.focus.ticking) member.focus.seconds else {
        val now by produceState(SystemClock.elapsedRealtime(), member.id) {
            while (true) { delay(1_000); value = SystemClock.elapsedRealtime() }
        }
        member.focus.displaySeconds(member.receivedAt, now)
    }
    val time = if (seconds < 3_600) "${seconds / 60}分 ${seconds % 60}秒"
        else "${seconds / 3_600}时 ${seconds / 60 % 60}分"
    Text(time, modifier, style = MaterialTheme.typography.labelMedium.copy(fontFamily = RoomNumerals, fontWeight = FontWeight.Medium, fontSize = 14.sp, fontFeatureSettings = "tnum", letterSpacing = 0.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
