package bo.com.hydor.pruebashidraulicas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bo.com.hydor.pruebashidraulicas.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val HydorBlue = Color(0xFF123A63)
private val HydorLightBlue = Color(0xFFEAF2F8)
private val HydorGreen = Color(0xFF26734D)
private val HydorAmber = Color(0xFFA96400)
private val HydorRed = Color(0xFFB3261E)
private val HydorBackground = Color(0xFFF5F7FA)

private object Routes {
    const val DASHBOARD = "dashboard"; const val NEW_TEST = "new_test"; const val PROJECTS = "projects"; const val REPORTS = "reports"; const val CALIBRATION = "gauge_calibration"
    const val TEST_READY = "test_ready/{testId}"; const val ACTIVE_TEST = "active_test/{testId}"; const val CAMERA_READING = "camera_reading/{testId}"; const val REVIEW_TEST = "review_test/{testId}"
    fun testReady(testId: Long) = "test_ready/$testId"; fun activeTest(testId: Long) = "active_test/$testId"; fun cameraReading(testId: Long) = "camera_reading/$testId"; fun reviewTest(testId: Long) = "review_test/$testId"
}

@Composable fun HydorApp() {
    val navController = rememberNavController()
    MaterialTheme(colorScheme = lightColorScheme(primary = HydorBlue, secondary = HydorGreen, background = HydorBackground, surface = Color.White)) {
        Scaffold(containerColor = HydorBackground, topBar = { HydorTopBar() }) { padding ->
            NavHost(navController = navController, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(padding)) {
                composable(Routes.DASHBOARD) { Dashboard(navController) }; composable(Routes.NEW_TEST) { NewHydraulicTestScreen(navController) }; composable(Routes.PROJECTS) { ProjectNetworkScreen { navController.popBackStack() } }; composable(Routes.REPORTS) { ConsolidatedReportsScreen(navController) }; composable(Routes.CALIBRATION) { GaugeCalibrationScreen { navController.popBackStack() } }
                composable(Routes.TEST_READY, arguments = listOf(navArgument("testId") { type = NavType.LongType })) { TestReadyScreen(navController, it.arguments?.getLong("testId") ?: 0L) }
                composable(Routes.ACTIVE_TEST, arguments = listOf(navArgument("testId") { type = NavType.LongType })) { ActiveTestScreen(navController, it.arguments?.getLong("testId") ?: 0L) }
                composable(Routes.CAMERA_READING, arguments = listOf(navArgument("testId") { type = NavType.LongType })) { entry -> val id=entry.arguments?.getLong("testId")?:0L; CameraReadingScreen(id,{navController.popBackStack()},{navController.popBackStack()}) }
                composable(Routes.REVIEW_TEST, arguments = listOf(navArgument("testId") { type = NavType.LongType })) { entry -> val id=entry.arguments?.getLong("testId")?:0L; TestReviewScreen(id,{navController.popBackStack()},{navController.navigate(Routes.REPORTS){popUpTo(Routes.DASHBOARD)}}) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) @Composable private fun HydorTopBar() {
    TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = HydorBlue, titleContentColor = Color.White), title = { Column { Text("PBC", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp); Text("LABORATORIO PBC BOLIVIA · PRUEBAS HIDRÁULICAS", fontSize = 10.sp) } })
}

@Composable private fun Dashboard(navController: NavHostController) {
    val context=LocalContext.current; val dao=remember{HydorDatabase.getInstance(context).hydorDao()}; var active by remember{mutableStateOf<HydraulicTestEntity?>(null)}; var section by remember{mutableStateOf<SectionEntity?>(null)}; var calibration by remember{mutableStateOf(GaugeCalibrationStore.load(context))}; var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(true){active=dao.getActiveTest();section=active?.let{dao.getSection(it.sectionId)};calibration=GaugeCalibrationStore.load(context);now=System.currentTimeMillis();delay(1000)}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("Laboratorio PBC Bolivia",fontSize=28.sp,fontWeight=FontWeight.Bold,color=HydorBlue);Text("Registro, seguimiento y evaluación técnica de pruebas hidráulicas, incluso sin conexión.")
        active?.let{test->val extra=TestExtraTimeStore.minutes(context,test.id);val total=(test.durationMinutes+extra)*60_000L;val remaining=max(0L,total-(now-test.startedAt));Card(colors=CardDefaults.cardColors(containerColor=HydorAmber.copy(alpha=.12f))){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("PRUEBA EN CURSO",fontWeight=FontWeight.ExtraBold,color=HydorAmber);Text(section?.let{"${it.startValve} → ${it.endValve}"}?:"Prueba #${test.id}",fontWeight=FontWeight.Bold);Text("Tiempo restante: ${formatClock(remaining)}");Button(onClick={navController.navigate(Routes.activeTest(test.id))},modifier=Modifier.fillMaxWidth()){Text("REANUDAR PRUEBA")}}}}
        Button(onClick={navController.navigate(Routes.NEW_TEST)},modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(14.dp)){Text("+  NUEVA PRUEBA HIDRÁULICA",fontWeight=FontWeight.Bold)}
        Card(onClick={navController.navigate(Routes.CALIBRATION)},colors=CardDefaults.cardColors(containerColor=if(calibration.isCalibrated)HydorGreen.copy(alpha=.10f)else HydorLightBlue)){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){PressureGauge(calibration.maxBar*.55,calibration.maxBar,Modifier.size(70.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("MANÓMETRO",fontSize=11.sp,fontWeight=FontWeight.Bold,color=HydorBlue);Text(calibration.name,fontWeight=FontWeight.Bold);Text("0–${formatNumber(calibration.maxBar)} bar · ${calibration.samples.size} puntos");Text(if(calibration.isCalibrated)"CALIBRADO · tocar para gestionar" else "CALIBRAR / APRENDER",color=if(calibration.isCalibrated)HydorGreen else HydorAmber,fontWeight=FontWeight.Bold,fontSize=12.sp)}}}
        HomeAction("Proyectos y tramos","Vista gráfica de la red y estado de cada tramo"){navController.navigate(Routes.PROJECTS)};HomeAction("Informes y resultados","Solo proyectos consolidados, con fecha y PDF final"){navController.navigate(Routes.REPORTS)}
    }
}
@Composable private fun HomeAction(title:String,subtitle:String,onClick:()->Unit){OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp,color=HydorBlue);Text(subtitle,fontSize=13.sp)}}}

@Composable private fun NewHydraulicTestScreen(navController:NavHostController){val context=LocalContext.current;val dao=remember{HydorDatabase.getInstance(context).hydorDao()};val scope=rememberCoroutineScope();val gauge=remember{GaugeCalibrationStore.load(context)};var project by remember{mutableStateOf("")};var battery by remember{mutableStateOf("")};var neighborhood by remember{mutableStateOf("")};var start by remember{mutableStateOf("")};var end by remember{mutableStateOf("")};var diameter by remember{mutableStateOf("")};var length by remember{mutableStateOf("")};var nominal by remember{mutableStateOf("")};var target by remember{mutableStateOf("")};var drop by remember{mutableStateOf("0.40")};var gaugeMax by remember{mutableStateOf(formatNumber(gauge.maxBar))};var hours by remember{mutableStateOf("4")};var operator by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)};val valid=project.isNotBlank()&&neighborhood.isNotBlank()&&start.isNotBlank()&&end.isNotBlank()&&diameter.isNotBlank()&&length.toDoubleOrNull()!=null&&nominal.toDoubleOrNull()!=null&&target.toDoubleOrNull()!=null&&drop.toDoubleOrNull()!=null&&gaugeMax.toDoubleOrNull()!=null&&hours.toDoubleOrNull()!=null
Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){SectionTitle("Identificación del trabajo","Usa las mismas referencias del plano y planilla.");HelpField("Proyecto / obra",project,"Ej.: Ampliación Sistema de Agua Potable"){project=it};HelpField("Batería o grupo de prueba (opcional)",battery,"Ej.: Batería 3"){battery=it};HelpField("Ubicación / barrio",neighborhood,"Ej.: Barrio San José"){neighborhood=it};SectionTitle("Tramo sometido a prueba","Usa referencias reales del plano.");HelpField("Punto inicial del tramo",start,"Ej.: T-35, V-02 o Nodo N-14"){start=it};HelpField("Punto final del tramo",end,"Ej.: T-34, V-03 o Nodo N-15"){end=it};HelpField("Diámetro de tubería (pulgadas \" )",diameter,"Ej.: 2, 4, 6, 8 o 1 1/2"){diameter=it};HelpField("Longitud del tramo (m)",length,"Ej.: 300.00"){length=it};SectionTitle("Criterio técnico","La presión de ensayo será el punto de partida oficial.");HelpField("Presión nominal (bar)",nominal,"Ej.: 10.00"){nominal=it};HelpField("Presión de ensayo / sometida (bar)",target,"Ej.: 7.00"){target=it};HelpField("Caída máxima permitida (bar)",drop,"Ej.: 0.40"){drop=it};HelpField("Escala máxima del manómetro (bar)",gaugeMax,"Manómetro activo: ${gauge.name}"){gaugeMax=it};HelpField("Duración del ensayo (horas)",hours,"Ej.: 4"){hours=it};HelpField("Operador responsable",operator,"Ej.: Ing. responsable"){operator=it};error?.let{Text(it,color=HydorRed)};Button(onClick={scope.launch{try{val projectId=dao.findProjectByName(project.trim())?.id?:dao.insertProject(ProjectEntity(name=project.trim(),location=neighborhood.trim()));val sectionId=dao.insertSection(SectionEntity(projectId=projectId,battery=battery.trim(),neighborhood=neighborhood.trim(),startValve=start.trim(),endValve=end.trim(),diameterInches=diameter.trim(),lengthMeters=length.toDouble()));val testId=dao.insertTest(HydraulicTestEntity(sectionId=sectionId,operatorName=operator.trim(),nominalPressureBar=nominal.toDouble(),targetPressureBar=target.toDouble(),maxAllowedDropBar=drop.toDouble(),gaugeMaxBar=gaugeMax.toDouble(),durationMinutes=(hours.toDouble()*60).toInt().coerceAtLeast(1),startedAt=0L,status="READY"));navController.navigate(Routes.testReady(testId))}catch(e:Exception){error=e.message?:"No se pudo guardar la prueba"}}},enabled=valid,modifier=Modifier.fillMaxWidth().height(58.dp)){Text("GUARDAR Y PREPARAR PRUEBA",fontWeight=FontWeight.Bold)}}}
@Composable private fun SectionTitle(title:String,subtitle:String){Column{Text(title,fontWeight=FontWeight.Bold,fontSize=19.sp,color=HydorBlue);Text(subtitle,fontSize=12.sp,color=Color(0xFF5F6368))}}
@Composable private fun HelpField(label:String,value:String,help:String,onChange:(String)->Unit){OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},supportingText={Text(help)},singleLine=true,modifier=Modifier.fillMaxWidth())}

@Composable private fun TestReadyScreen(navController:NavHostController,testId:Long){val context=LocalContext.current;val dao=remember{HydorDatabase.getInstance(context).hydorDao()};val scope=rememberCoroutineScope();var test by remember{mutableStateOf<HydraulicTestEntity?>(null)};var section by remember{mutableStateOf<SectionEntity?>(null)};LaunchedEffect(testId){test=dao.getTest(testId);section=test?.let{dao.getSection(it.sectionId)}};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){SectionTitle("Preparación de la prueba","Confirma físicamente el tramo antes de iniciar.");test?.let{TechnicalSummary(section,it)};PreparationItem("Tubería completamente llena por gravedad");PreparationItem("Aire purgado por el punto más alto");PreparationItem("Extremos, válvulas y accesorios asegurados");PreparationItem("Manómetro instalado y visible");Button(onClick={val current=test?:return@Button;scope.launch{val startTime=System.currentTimeMillis();dao.startTest(testId,startTime);val existing=dao.getReadings(testId);if(existing.none{it.source=="PROGRAMMED"})dao.insertReading(PressureReadingEntity(testId,startTime,null,current.targetPressureBar,null,null,"PROGRAMMED"));navController.navigate(Routes.activeTest(testId))}},enabled=test!=null,modifier=Modifier.fillMaxWidth().height(58.dp)){Text("INICIAR ENSAYO",fontWeight=FontWeight.Bold)}}}
@Composable private fun TechnicalSummary(section:SectionEntity?,test:HydraulicTestEntity){Card(colors=CardDefaults.cardColors(containerColor=HydorLightBlue),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){section?.let{Text("Tramo: ${it.startValve} → ${it.endValve}");Text("${formatNumber(it.lengthMeters)} m · Ø ${formatDiameter(it.diameterInches)}")};HorizontalDivider();Text("Presión de ensayo / punto inicial: ${formatNumber(test.targetPressureBar)} bar",fontWeight=FontWeight.Bold);Text("Caída máxima: ${formatNumber(test.maxAllowedDropBar)} bar");Text("Límite mínimo: ${formatNumber(test.targetPressureBar-test.maxAllowedDropBar)} bar");Text("Duración: ${formatDuration(test.durationMinutes)}")}}}
@Composable private fun PreparationItem(text:String){OutlinedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Text("✓",color=HydorGreen,fontWeight=FontWeight.Bold);Spacer(Modifier.width(12.dp));Text(text)}}}
private data class Evaluation(val title:String,val description:String,val color:Color,val drop:Double,val remaining:Double)
private fun evaluate(readings:List<PressureReadingEntity>,startPressure:Double,allowed:Double):Evaluation{val current=readings.lastOrNull()?.confirmedPressureBar?:startPressure;val d=max(0.0,startPressure-current);val r=allowed-d;return when{d>allowed->Evaluation("FUERA DE RANGO","La presión cayó por debajo del límite permitido.",HydorRed,d,r);d>=allowed*.75->Evaluation("ATENCIÓN","La presión está cerca del límite permitido.",HydorAmber,d,r);else->Evaluation("NORMAL","La presión permanece dentro del rango configurado.",HydorGreen,d,r)}}

@Composable private fun ActiveTestScreen(navController:NavHostController,testId:Long){val context=LocalContext.current;val dao=remember{HydorDatabase.getInstance(context).hydorDao()};val scope=rememberCoroutineScope();var test by remember{mutableStateOf<HydraulicTestEntity?>(null)};var section by remember{mutableStateOf<SectionEntity?>(