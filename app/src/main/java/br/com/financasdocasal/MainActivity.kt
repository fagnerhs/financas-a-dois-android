package br.com.financasdocasal

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Expense(val id: Long=0, val amount: Double, val category: String, val payer: String, val description: String, val date: String)
object Categories { val all=listOf("Alimentação","Mercado","Moradia","Transporte","Lazer","Saúde","Educação","Delivery","Outros") }
object Parser {
    fun parse(text: String, names: List<String>, now: Date=Date()): Expense? {
        val amount=Regex("(\\d+(?:[.,]\\d{1,2})?)").find(text)?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: return null
        val s=text.lowercase(Locale("pt","BR")); val cat=when {
            listOf("mercado","supermercado","feira").any{s.contains(it)}->"Mercado"
            listOf("delivery","ifood","entrega").any{s.contains(it)}->"Delivery"
            listOf("uber","99","taxi","ônibus","onibus","combustível","combustivel").any{s.contains(it)}->"Transporte"
            listOf("aluguel","condomínio","condominio","luz","água","agua","internet").any{s.contains(it)}->"Moradia"
            listOf("farmácia","farmacia","médico","medico","saúde","saude").any{s.contains(it)}->"Saúde"
            listOf("escola","curso","faculdade").any{s.contains(it)}->"Educação"
            listOf("cinema","bar","show","passeio").any{s.contains(it)}->"Lazer"
            listOf("restaurante","almoço","almoco","jantar","café","cafe").any{s.contains(it)}->"Alimentação"
            else->"Outros"
        }
        val payer=names.firstOrNull { it.isNotBlank() && s.contains(it.lowercase(Locale("pt","BR"))) } ?: "Não informado"
        val cal=Calendar.getInstance().apply { time=now }; if(s.contains("ontem")) cal.add(Calendar.DAY_OF_YEAR,-1)
        val date=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.time)
        return Expense(amount=amount,category=cat,payer=payer,description=text,date=date)
    }
}
class Store(ctx: Context): SQLiteOpenHelper(ctx,"financas.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL, category TEXT, payer TEXT, description TEXT, date TEXT)") }
    override fun onUpgrade(db: SQLiteDatabase,oldVersion:Int,newVersion:Int) {}
    fun add(e:Expense) { writableDatabase.execSQL("INSERT INTO expenses(amount,category,payer,description,date) VALUES(?,?,?,?,?)",arrayOf(e.amount,e.category,e.payer,e.description,e.date)) }
    fun all():List<Expense> { val out=mutableListOf<Expense>(); readableDatabase.rawQuery("SELECT id,amount,category,payer,description,date FROM expenses ORDER BY date DESC,id DESC",null).use { c->while(c.moveToNext()) out.add(Expense(c.getLong(0),c.getDouble(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))) }; return out }
    fun importJson(s:String) { val a=JSONArray(s); val db=writableDatabase; db.beginTransaction(); try { for(i in 0 until a.length()){val o=a.getJSONObject(i);add(Expense(amount=o.getDouble("valor"),category=o.getString("categoria"),payer=o.optString("quem","Não informado"),description=o.optString("descricao","Importado"),date=o.getString("data")))};db.setTransactionSuccessful() } finally {db.endTransaction()} }
}
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF176B55), secondary=Color(0xFF64A88E))) { FinanceApp(Store(this)) } }}}

@Composable fun FinanceApp(db:Store) {
    val ctx= LocalContext.current; val prefs=remember { ctx.getSharedPreferences("prefs",Context.MODE_PRIVATE) }
    var names by remember { mutableStateOf(prefs.getString("names","Ana,João") ?: "Ana,João") }
    var limitsText by remember { mutableStateOf(prefs.getString("limits","") ?: "") }
    var goal by remember { mutableStateOf(prefs.getString("goal","10000") ?: "10000") }; var saved by remember { mutableStateOf(prefs.getString("saved","0") ?: "0") }; var monthly by remember { mutableStateOf(prefs.getString("monthly","500") ?: "500") }
    var rows by remember { mutableStateOf(db.all()) }; var text by remember { mutableStateOf("") }; var status by remember { mutableStateOf("") }; var tab by remember { mutableIntStateOf(0) }; var importText by remember { mutableStateOf("") }
    val nameList=names.split(',').map{it.trim()}.filter{it.isNotEmpty()}; val current=rows.filter{it.date.startsWith(SimpleDateFormat("yyyy-MM",Locale.US).format(Date()))}
    val limits=remember(limitsText){ limitsText.split('\n').mapNotNull { line-> val p=line.split('='); if(p.size==2) p[0].trim() to (p[1].trim().toDoubleOrNull()?:0.0) else null }.toMap() }
    val spent=current.sumOf{it.amount}; val budget=limits.values.sum()
    Scaffold(bottomBar={NavigationBar { listOf("Registrar","Painel","Insights","Ajustes").forEachIndexed{ i,label->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={},label={Text(label)})} }}) { pad->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Finanças a Dois",style=MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp))
            when(tab){
                0->{Text("Uma frase basta",style=MaterialTheme.typography.titleLarge); Text("Ex.: gastei 50 no mercado"); Spacer(Modifier.height(8.dp)); OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),label={Text("O que vocês gastaram?")}); Spacer(Modifier.height(8.dp)); Button(onClick={val e=Parser.parse(text,nameList);if(e==null) status="Não encontrei um valor. Escreva, por exemplo: gastei 50 no mercado." else {db.add(e);rows=db.all();val lim=limits[e.category]?:0.0;val catTotal=current.filter{it.category==e.category}.sumOf{it.amount}+e.amount;status="✓ R$ ${"%.2f".format(e.amount)} · ${e.category} · ${e.payer}"+(if(lim>0&&catTotal>lim)" — limite ultrapassado!" else "");text=""}},Modifier.fillMaxWidth()){Text("Registrar")}; if(status.isNotEmpty()) Text(status,color=if(status.contains("ultrapassado")) Color.Red else MaterialTheme.colorScheme.primary); Text("Processamento local/offline; confira a categoria antes de salvar.",style=MaterialTheme.typography.bodySmall) }
                1->{val over=limits.keys.mapNotNull{c-> val x=current.filter{it.category==c}.sumOf{it.amount}; val l=limits[c]?:0.0; if(l>0&&x/l>=.8) c to (x/l) else null}; if(over.isNotEmpty()) Card(colors=CardDefaults.cardColors(containerColor=if(over.any{it.second>1})Color(0xFFFFDAD6) else Color(0xFFFFF0C2)),modifier=Modifier.fillMaxWidth()){Text(if(over.any{it.second>1})"Limite ultrapassado: ${over.filter{it.second>1}.joinToString{it.first}}" else "Atenção: categoria acima de 80% do limite",Modifier.padding(12.dp))}; Text("Gasto no mês: R$ ${"%.2f".format(spent)}");Text("Orçamento: R$ ${"%.2f".format(budget)} · Restante: R$ ${"%.2f".format(budget-spent)}"); Spacer(Modifier.height(8.dp)); limits.forEach{(cat,lim)->val v=current.filter{it.category==cat}.sumOf{it.amount};Text("$cat  R$ ${"%.2f".format(v)} / R$ ${"%.2f".format(lim)}");LinearProgressIndicator(progress={if(lim>0)(v/lim).toFloat().coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth(),color=if(lim>0&&v/lim>1)Color.Red else if(lim>0&&v/lim>=.8)Color(0xFFFFA000) else MaterialTheme.colorScheme.primary)}; Text("Transações recentes",style=MaterialTheme.typography.titleMedium); rows.take(10).forEach{Text("${it.date} · ${it.category} · ${it.payer} · R$ ${"%.2f".format(it.amount)}")}}
                2->{Text("Resumo e próximos passos",style=MaterialTheme.typography.titleLarge); Button(onClick={val max=limits.keys.maxByOrNull{c->current.filter{it.category==c}.sumOf{it.amount}}?:"Sem dados";val g=goal.toDoubleOrNull()?:0.0;val s=saved.toDoubleOrNull()?:0.0;val m=monthly.toDoubleOrNull()?:0.0; val months=if(g<=s)0 else if(m>0) kotlin.math.ceil((g-s)/m).toInt() else -1;status="Categoria com maior gasto: $max. Gasto neste mês: R$ ${"%.2f".format(spent)}. Sugestão: revejam compras em $max e definam um corte mensal. Fundo de emergência: ${if(months>=0)"$months meses ao aporte atual" else "defina um aporte mensal para estimar"}."}){Text("Analisar meu mês")}; if(status.isNotBlank()) Text(status);Text("Análise calculada localmente com os dados deste aparelho.")}
                else->{Text("Configurações",style=MaterialTheme.typography.titleLarge);OutlinedTextField(names,{names=it},Modifier.fillMaxWidth(),label={Text("Nomes separados por vírgula")}); Text("Limites mensais (uma linha por categoria: Mercado=1200)");OutlinedTextField(limitsText,{limitsText=it},Modifier.fillMaxWidth(),minLines=3);OutlinedTextField(goal,{goal=it},label={Text("Meta do fundo (R$)")});OutlinedTextField(saved,{saved=it},label={Text("Saldo atual do fundo (R$)")});OutlinedTextField(monthly,{monthly=it},label={Text("Aporte mensal (R$)")});Button(onClick={prefs.edit().putString("names",names).putString("limits",limitsText).putString("goal",goal).putString("saved",saved).putString("monthly",monthly).apply();status="Configurações salvas neste aparelho."}){Text("Salvar configurações")};Button(onClick={val arr=JSONArray();rows.forEach{e->arr.put(JSONObject().put("valor",e.amount).put("categoria",e.category).put("quem",e.payer).put("descricao",e.description).put("data",e.date))}; val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,arr.toString());ctx.startActivity(Intent.createChooser(send,"Exportar dados JSON"))}){Text("Exportar dados")};OutlinedTextField(importText,{importText=it},Modifier.fillMaxWidth(),label={Text("Cole aqui o JSON exportado")});Button(onClick={try{db.importJson(importText);rows=db.all();status="Importação concluída."}catch(e:Exception){status="JSON inválido; verifique e tente novamente."}}){Text("Importar dados")};Text(status) }
            }
        }
    }
}
