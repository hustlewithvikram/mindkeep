package com.vikram.mindkeep.feature
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ConnectorsPage(topPadding:Dp) {
 Column(Modifier.fillMaxSize().padding(start=20.dp,end=20.dp,top=topPadding,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Text("Integrations",style=MaterialTheme.typography.headlineLarge)
  Text("Choose which services MindKeep may use. Every connection will require explicit permission.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  ConnectorCard(Icons.Default.NoteAlt,"Google Keep","Read and create notes","Not connected")
  ConnectorCard(Icons.Default.CalendarMonth,"Google Calendar","Events and schedules","Not connected")
  ConnectorCard(Icons.Default.Cloud,"MCP connectors","External tools and servers","Framework planned")
  ConnectorCard(Icons.Default.Hub,"Local actions","Notes, tasks and reminders","Local foundation")
  Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
   Text("OAuth, secure token storage, MCP transport, tool discovery and per-action approval still need implementation. No external account is connected yet.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
@Composable
private fun ConnectorCard(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,detail:String,state:String) {
 Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
  Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
   Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
    Text(title,style=MaterialTheme.typography.titleMedium)
    Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(state,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.tertiary)
   }
  }
 }
}
