package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.MilestoneDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.GoalMilestoneEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.ProjectMilestoneEntity
import com.example.lifeos.domain.usecases.GoalProjectProgressUseCase
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val goalDao: GoalDao,
    private val projectDao: ProjectDao,
    private val milestoneDao: MilestoneDao,
    private val progressUseCase: GoalProjectProgressUseCase
) : ViewModel() {

    private val _goals = MutableStateFlow<List<GoalEntity>>(emptyList())
    val goals: StateFlow<List<GoalEntity>> = _goals.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectEntity>>(emptyList())
    val projects: StateFlow<List<ProjectEntity>> = _projects.asStateFlow()

    // Per-id caches for getMilestonesForGoal/getMilestonesForProject below —
    // see the comment on getMilestonesForGoal for why this exists.
    private val goalMilestoneFlows = mutableMapOf<String, StateFlow<List<GoalMilestoneEntity>>>()
    private val projectMilestoneFlows = mutableMapOf<String, StateFlow<List<ProjectMilestoneEntity>>>()

    init {
        viewModelScope.launch {
            goalDao.getAllGoals().collect { _goals.value = it }
        }
        viewModelScope.launch {
            projectDao.getAllProjects().collect { _projects.value = it }
        }
    }

    fun addGoal(title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            goalDao.insertGoal(
                GoalEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    description = description.ifBlank { null }
                )
            )
        }
    }

    fun addProject(name: String, description: String, goalId: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            projectDao.insertProject(
                ProjectEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = description.ifBlank { null },
                    goalId = goalId
                )
            )
        }
    }

    /** Updates only title/description (prompt section 15: "Edit goals") — progress/milestones/links are untouched. */
    fun updateGoalDetails(goal: GoalEntity, title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            goalDao.updateGoal(goal.copy(title = title, description = description.ifBlank { null }))
        }
    }

    /**
     * Deletes a goal. Projects previously linked to it are NOT deleted —
     * only unlinked (goalId set to null) — since a project is its own
     * independent unit of work (prompt section 16) that shouldn't vanish
     * just because the goal organizing it was removed.
     */
    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch {
            projectDao.getProjectsByGoal(goal.id).first().forEach { project ->
                projectDao.updateProject(project.copy(goalId = null))
            }
            goalDao.deleteGoal(goal)
        }
    }

    /** Updates only name/description (prompt section 16: "Edit projects") — progress/milestones/links are untouched. */
    fun updateProjectDetails(project: ProjectEntity, name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            projectDao.updateProject(project.copy(name = name, description = description.ifBlank { null }))
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch { projectDao.deleteProject(project) }
    }

    fun getMilestonesForGoal(goalId: String): StateFlow<List<GoalMilestoneEntity>> {
        // Memoized per goalId — previously this rebuilt a brand-new StateFlow
        // (starting at emptyList()) on every recomposition of GoalCard,
        // since GoalCard calls this directly in its @Composable body. Each
        // fresh StateFlow briefly showed an empty milestone list before its
        // own DB read caught up, which is what produced the visible "jump"
        // every time a milestone was added (progress change on the parent
        // Goal list recomposes GoalCard, which used to hand back yet
        // another new, momentarily-empty flow).
        return goalMilestoneFlows.getOrPut(goalId) {
            milestoneDao.getMilestonesForGoal(goalId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    fun toggleGoalMilestone(milestone: GoalMilestoneEntity) {
        viewModelScope.launch {
            milestoneDao.updateGoalMilestone(milestone.copy(isCompleted = !milestone.isCompleted))
            refreshGoalProgress(milestone.goalId)
        }
    }

    fun deleteGoalMilestone(milestone: GoalMilestoneEntity) {
        viewModelScope.launch {
            milestoneDao.deleteGoalMilestone(milestone)
            refreshGoalProgress(milestone.goalId)
        }
    }

    fun addGoalMilestone(goalId: String, title: String, position: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            milestoneDao.insertGoalMilestone(
                GoalMilestoneEntity(goalId = goalId, title = title, position = position)
            )
            refreshGoalProgress(goalId)
        }
    }

    /**
     * Recomputes this goal's progress (prompt sections 15-16: milestone-based
     * when milestones exist, else task-completion ratio — see
     * [GoalProjectProgressUseCase]) and persists it to
     * [GoalEntity.progressPercentage] so every screen that reads the stored
     * field (e.g. Analytics) sees an up-to-date number, not the permanent 0
     * it used to be stuck at.
     */
    private suspend fun refreshGoalProgress(goalId: String) {
        val percentage = progressUseCase.computeGoalProgress(goalId)
        val goal = _goals.value.find { it.id == goalId } ?: goalDao.getGoalById(goalId) ?: return
        goalDao.updateGoal(goal.copy(progressPercentage = percentage))
    }

    fun getMilestonesForProject(projectId: String): StateFlow<List<ProjectMilestoneEntity>> {
        return projectMilestoneFlows.getOrPut(projectId) {
            milestoneDao.getMilestonesForProject(projectId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    fun addProjectMilestone(projectId: String, title: String, position: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            milestoneDao.insertProjectMilestone(
                ProjectMilestoneEntity(projectId = projectId, title = title, position = position)
            )
            refreshProjectProgress(projectId)
        }
    }

    fun toggleProjectMilestone(milestone: ProjectMilestoneEntity) {
        viewModelScope.launch {
            milestoneDao.updateProjectMilestone(milestone.copy(isCompleted = !milestone.isCompleted))
            refreshProjectProgress(milestone.projectId)
        }
    }

    fun deleteProjectMilestone(milestone: ProjectMilestoneEntity) {
        viewModelScope.launch {
            milestoneDao.deleteProjectMilestone(milestone)
            refreshProjectProgress(milestone.projectId)
        }
    }

    private suspend fun refreshProjectProgress(projectId: String) {
        val percentage = progressUseCase.computeProjectProgress(projectId)
        val project = _projects.value.find { it.id == projectId } ?: return
        projectDao.updateProject(project.copy(progressPercentage = percentage))
    }

    /**
     * A goal's progress can also move because a linked task was
     * completed/uncompleted elsewhere (Today, Tasks, AI) rather than through
     * a milestone toggle here — recompute on demand so the displayed number
     * is never stale just because this screen wasn't open when the task
     * changed.
     */
    suspend fun refreshAllProgress() {
        _goals.value.forEach { refreshGoalProgress(it.id) }
        _projects.value.forEach { refreshProjectProgress(it.id) }
    }
}

@Composable
fun GoalsScreen(viewModel: ProjectsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsState()
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var goalBeingEdited by remember { mutableStateOf<GoalEntity?>(null) }

    // Refresh progress on entering the screen so a task completed elsewhere
    // (Today/Tasks/AI) since the last visit is reflected immediately, not
    // just after the next milestone toggle.
    LaunchedEffect(goals.map { it.id }) {
        viewModel.refreshAllProgress()
    }

    val isLight = !LocalIsDarkTheme.current
    val bgGradient = if (isLight) {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "اهداف",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "چیزهایی که می‌خواهید در بلندمدت به آن‌ها برسید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (goals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().glassCard().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هنوز هدفی تعریف نشده", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(goals) { goal ->
                        GoalCard(
                            goal = goal,
                            viewModel = viewModel,
                            onEdit = { goalBeingEdited = goal },
                            onDelete = { viewModel.deleteGoal(goal) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddGoalDialog = true },
            containerColor = AccentAmber,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "هدف جدید")
        }

        if (showAddGoalDialog) {
            SimpleAddDialog(
                title = "هدف جدید",
                nameLabel = "عنوان هدف",
                onDismiss = { showAddGoalDialog = false },
                onAdd = { name, desc -> viewModel.addGoal(name, desc); showAddGoalDialog = false }
            )
        }

        goalBeingEdited?.let { goal ->
            SimpleAddDialog(
                title = "ویرایش هدف",
                nameLabel = "عنوان هدف",
                confirmLabel = "ذخیره تغییرات",
                initialName = goal.title,
                initialDescription = goal.description.orEmpty(),
                onDismiss = { goalBeingEdited = null },
                onAdd = { name, desc ->
                    viewModel.updateGoalDetails(goal, name, desc)
                    goalBeingEdited = null
                }
            )
        }
    }
}

@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel = hiltViewModel()) {
    val projects by viewModel.projects.collectAsState()
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var projectBeingEdited by remember { mutableStateOf<ProjectEntity?>(null) }

    LaunchedEffect(projects.map { it.id }) {
        viewModel.refreshAllProgress()
    }

    // Dynamic gradient background depending on Light/Dark mode (previously
    // hardcoded to always show the dark gradient regardless of theme setting,
    // and even after that fix, relying on a fragile exact-color comparison
    // against the light background constant). We now read the same
    // isDarkMode flag the rest of the app uses via LifeOSTheme, so this stays
    // correct even if the light/dark palette constants change later.
    val isLight = !LocalIsDarkTheme.current
    val bgGradient = if (isLight) {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "پروژه‌ها",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "کارهایی که در حال پیش‌بردن آن‌ها هستید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().glassCard().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هنوز پروژه‌ای تعریف نشده", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(projects) { project ->
                        ProjectCard(
                            project = project,
                            viewModel = viewModel,
                            onEdit = { projectBeingEdited = project },
                            onDelete = { viewModel.deleteProject(project) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddProjectDialog = true },
            containerColor = AccentBlue,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "پروژه جدید")
        }

        if (showAddProjectDialog) {
            SimpleAddDialog(
                title = "پروژه جدید",
                nameLabel = "نام پروژه",
                onDismiss = { showAddProjectDialog = false },
                onAdd = { name, desc -> viewModel.addProject(name, desc, null); showAddProjectDialog = false }
            )
        }

        projectBeingEdited?.let { project ->
            SimpleAddDialog(
                title = "ویرایش پروژه",
                nameLabel = "نام پروژه",
                confirmLabel = "ذخیره تغییرات",
                initialName = project.name,
                initialDescription = project.description.orEmpty(),
                onDismiss = { projectBeingEdited = null },
                onAdd = { name, desc ->
                    viewModel.updateProjectDetails(project, name, desc)
                    projectBeingEdited = null
                }
            )
        }
    }
}

@Composable
fun GoalCard(
    goal: GoalEntity,
    viewModel: ProjectsViewModel,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val milestones by viewModel.getMilestonesForGoal(goal.id).collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var newMilestoneTitle by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val completed = milestones.count { it.isCompleted }

    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    goal.description?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                    if (milestones.isNotEmpty()) {
                        Text(
                            "مایلستون‌ها: $completed/${milestones.size}",
                            color = AccentAmber,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // Real, computed progress (prompt sections 15-16), not a
                    // stored value nothing ever set.
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "پیشرفت هدف: ${goal.progressPercentage} درصد"
                        }
                    ) {
                        LinearProgressIndicator(
                            progress = { goal.progressPercentage / 100f },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = AccentAmber,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${goal.progressPercentage}٪",
                            color = AccentAmber,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "گزینه‌های بیشتر",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("ویرایش") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف", color = AccentRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                milestones.forEach { milestone ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = milestone.isCompleted,
                            onCheckedChange = { viewModel.toggleGoalMilestone(milestone) }
                        )
                        Text(
                            milestone.title,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (milestone.isCompleted) TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { viewModel.deleteGoalMilestone(milestone) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = AccentRed)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newMilestoneTitle,
                        onValueChange = { newMilestoneTitle = it },
                        label = { Text("مایلستون جدید") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newMilestoneTitle.isNotBlank()) {
                            viewModel.addGoalMilestone(goal.id, newMilestoneTitle, milestones.size)
                            newMilestoneTitle = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "افزودن", tint = AccentAmber)
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectCard(
    project: ProjectEntity,
    viewModel: ProjectsViewModel,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val milestones by viewModel.getMilestonesForProject(project.id).collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var newMilestoneTitle by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val completed = milestones.count { it.isCompleted }

    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    project.description?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "وضعیت: ${project.status}",
                        color = AccentTeal,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (milestones.isNotEmpty()) {
                        Text(
                            "مایلستون‌ها: $completed/${milestones.size}",
                            color = AccentTeal,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "پیشرفت پروژه: ${project.progressPercentage} درصد"
                        }
                    ) {
                        LinearProgressIndicator(
                            progress = { project.progressPercentage / 100f },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = AccentTeal,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${project.progressPercentage}٪",
                            color = AccentTeal,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "گزینه‌های بیشتر",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("ویرایش") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف", color = AccentRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                milestones.forEach { milestone ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = milestone.isCompleted,
                            onCheckedChange = { viewModel.toggleProjectMilestone(milestone) }
                        )
                        Text(
                            milestone.title,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (milestone.isCompleted) TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { viewModel.deleteProjectMilestone(milestone) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = AccentRed)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newMilestoneTitle,
                        onValueChange = { newMilestoneTitle = it },
                        label = { Text("مایلستون جدید") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newMilestoneTitle.isNotBlank()) {
                            viewModel.addProjectMilestone(project.id, newMilestoneTitle, milestones.size)
                            newMilestoneTitle = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "افزودن", tint = AccentTeal)
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleAddDialog(
    title: String,
    nameLabel: String,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    initialName: String = "",
    initialDescription: String = "",
    confirmLabel: String = "ذخیره"
) {
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("توضیحات (اختیاری)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onAdd(name, desc) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
