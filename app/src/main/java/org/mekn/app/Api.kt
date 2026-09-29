package org.mekn.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ---------- Data models ----------

data class Paper(
    val title: String, val authors: String, val journal: String, val year: String,
    val level: Level, val abstract: String, val url: String
)

data class OutcomeResult(val title: String, val values: List<String>, val pValue: String)

data class Trial(
    val id: String, val title: String, val status: String, val phase: String,
    val conditions: String, val url: String,
    val enrollment: String, val primaryOutcome: String, val whyStopped: String, val completion: String,
    val hasResults: Boolean, val outcome: OutcomeResult?, val safety: List<String>
)

data class TrialSummary(val total: Int, val recruiting: Int, val trials: List<Trial>)

data class Compound(
    val cid: String, val formula: String, val weight: String, val iupac: String, val inchikey: String,
    val xlogp: String, val tpsa: String, val donors: String, val acceptors: String,
    val rotatable: String, val heavyAtoms: String, val exactMass: String, val complexity: String,
    val url: String
)

data class DrugLabel(
    val brand: String, val generic: String, val manufacturer: String,
    val uses: String, val warnings: String, val interactions: String, val contraindications: String
)

data class Approval(
    val appNo: String, val sponsor: String, val brand: String, val form: String,
    val marketing: String, val approvedOn: String
)
data class FdaApprovals(val total: Int, val items: List<Approval>)

data class Tally(val term: String, val count: Int)
data class Market(val categories: List<Tally>, val makers: List<Tally>, val forms: List<Tally>) {
    val total: Int get() = categories.sumOf { it.count }
}

data class Recall(val firm: String, val reason: String, val classification: String, val date: String, val status: String)
data class Recalls(val total: Int, val items: List<Recall>)

data class EvidenceCount(val level: Level, val label: String, val count: Int)

/** Evidence level derived from publication-type tags (not a quality appraisal). */
enum class Level(val code: String, val human: Boolean) {
    L2("L2", true),        // systematic review / meta-analysis
    L3("L3", true),        // clinical trial
    L4("L4", true),        // observational / case report
    REVIEW("Rev", false),  // narrative review, not graded
    OTHER("—", false)      // untagged or other study types
}

// ---------- Public APIs (free, no key needed) ----------

object Api {

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** HTTP status (-1 = no connection) and parsed JSON body when successful. */
    private fun request(url: String): Pair<Int, JSONObject?> = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "MENK/0.6 (Android; evidence research app)")
        val code = conn.responseCode
        val body = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        conn.disconnect()
        code to body?.let { JSONObject(it) }
    } catch (e: Exception) {
        -1 to null
    }

    private fun get(url: String): JSONObject? = request(url).second

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun clean(s: String): String =
        s.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()

    private fun ymd(s: String): String =
        if (s.length == 8 && s.all { it.isDigit() }) "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}" else s

    private fun levelOf(types: List<String>): Level {
        val t = types.map { it.lowercase() }
        return when {
            t.any { "meta-analysis" in it || "systematic review" in it } -> Level.L2
            t.any { "clinical trial" in it || "randomized controlled trial" in it } -> Level.L3
            t.any { "observational study" in it || "case report" in it } -> Level.L4
            t.any { it == "review" || it == "review-article" } -> Level.REVIEW
            else -> Level.OTHER
        }
    }

    // ----- Literature: Europe PMC (includes PubMed) -----

    fun papers(q: String): List<Paper>? {
        val root = get(
            "https://www.ebi.ac.uk/europepmc/webservices/rest/search" +
                "?query=${enc(q)}&format=json&resultType=core&pageSize=25"
        ) ?: return null
        return root.optJSONObject("resultList")?.optJSONArray("result").objects().map { o ->
            Paper(
                title = clean(o.optString("title")),
                authors = o.optString("authorString"),
                journal = o.optJSONObject("journalInfo")?.optJSONObject("journal")?.optString("title") ?: "",
                year = o.optString("pubYear"),
                level = levelOf(o.optJSONObject("pubTypeList")?.optJSONArray("pubType").strings()),
                abstract = clean(o.optString("abstractText")),
                url = "https://europepmc.org/article/${o.optString("source")}/${o.optString("id")}"
            )
        }
    }

    fun evidenceMap(q: String): List<EvidenceCount>? {
        val rows = listOf(
            Triple(Level.L2, "Systematic reviews & meta-analyses",
                "(PUB_TYPE:\"Systematic Review\" OR PUB_TYPE:\"Meta-Analysis\")"),
            Triple(Level.L3, "Clinical trial reports", "PUB_TYPE:\"Clinical Trial\""),
            Triple(Level.L4, "Observational studies", "PUB_TYPE:\"Observational Study\""),
            Triple(Level.REVIEW, "Reviews (not graded)", "PUB_TYPE:\"Review\""),
            Triple(Level.OTHER, "All publications", "")
        )
        return rows.map { (level, label, filter) ->
            val query = if (filter.isEmpty()) q else "($q) AND $filter"
            val root = get(
                "https://www.ebi.ac.uk/europepmc/webservices/rest/search" +
                    "?query=${enc(query)}&format=json&resultType=idlist&pageSize=1"
            ) ?: return null
            EvidenceCount(level, label, root.optInt("hitCount", 0))
        }
    }

    // ----- Clinical trials: ClinicalTrials.gov API v2, including posted results -----

    fun trials(q: String): TrialSummary? {
        val base = "https://clinicaltrials.gov/api/v2/studies?query.term=${enc(q)}&countTotal=true"
        val root = get("$base&pageSize=15") ?: return null
        val recruiting = get("$base&pageSize=1&filter.overallStatus=RECRUITING")?.optInt("totalCount", 0) ?: 0
        val list = root.optJSONArray("studies").objects().mapNotNull { study ->
            val ps = study.optJSONObject("protocolSection") ?: return@mapNotNull null
            val rs = study.optJSONObject("resultsSection")
            val idm = ps.optJSONObject("identificationModule")
            val sm = ps.optJSONObject("statusModule")
            val dm = ps.optJSONObject("designModule")
            val id = idm?.optString("nctId") ?: ""
            val enrol = dm?.optJSONObject("enrollmentInfo")
            Trial(
                id = id,
                title = idm?.optString("briefTitle") ?: "",
                status = (sm?.optString("overallStatus") ?: "").replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() },
                phase = dm?.optJSONArray("phases").strings()
                    .joinToString(", ") { it.replace("EARLY_", "Early ").replace("PHASE", "Phase ").replace("NA", "Not applicable") }
                    .ifBlank { dm?.optString("studyType") ?: "" },
                conditions = ps.optJSONObject("conditionsModule")?.optJSONArray("conditions").strings()
                    .take(3).joinToString(", "),
                url = "https://clinicaltrials.gov/study/$id",
                enrollment = enrol?.let {
                    val n = it.optInt("count", -1)
                    if (n < 0) "" else "$n participants (${it.optString("type").lowercase().ifBlank { "count" }})"
                } ?: "",
                primaryOutcome = ps.optJSONObject("outcomesModule")?.optJSONArray("primaryOutcomes")
                    ?.optJSONObject(0)?.optString("measure") ?: "",
                whyStopped = sm?.optString("whyStopped") ?: "",
                completion = sm?.optJSONObject("completionDateStruct")?.optString("date") ?: "",
                hasResults = study.optBoolean("hasResults", rs != null),
                outcome = rs?.let { primaryResult(it) },
                safety = rs?.let { seriousEvents(it) } ?: emptyList()
            )
        }
        return TrialSummary(root.optInt("totalCount", list.size), recruiting, list)
    }

    private fun primaryResult(rs: JSONObject): OutcomeResult? {
        val m = rs.optJSONObject("outcomeMeasuresModule")?.optJSONArray("outcomeMeasures").objects()
            .firstOrNull { it.optString("type") == "PRIMARY" } ?: return null
        val names = m.optJSONArray("groups").objects().associate { it.optString("id") to it.optString("title") }
        val unit = m.optString("unitOfMeasure")
        val values = m.optJSONArray("classes")?.optJSONObject(0)?.optJSONArray("categories")?.optJSONObject(0)
            ?.optJSONArray("measurements").objects().mapNotNull { x ->
                val v = x.optString("value")
                if (v.isBlank()) null else "${names[x.optString("groupId")] ?: x.optString("groupId")}: $v $unit".trim()
            }
        val p = m.optJSONArray("analyses")?.optJSONObject(0)?.optString("pValue") ?: ""
        return OutcomeResult(m.optString("title"), values.take(4), p)
    }

    private fun seriousEvents(rs: JSONObject): List<String> =
        rs.optJSONObject("adverseEventsModule")?.optJSONArray("eventGroups").objects().mapNotNull { g ->
            val affected = g.optInt("seriousNumAffected", -1)
            val atRisk = g.optInt("seriousNumAtRisk", -1)
            if (affected < 0 || atRisk <= 0) null
            else "${g.optString("title")}: $affected of $atRisk had a serious adverse event"
        }.take(4)

    // ----- Chemistry: PubChem -----

    fun compound(name: String): Compound? {
        val path = enc(name.trim()).replace("+", "%20")
        val props = "MolecularFormula,MolecularWeight,IUPACName,InChIKey,XLogP,TPSA," +
            "HBondDonorCount,HBondAcceptorCount,RotatableBondCount,HeavyAtomCount,ExactMass,Complexity"
        val root = get("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/$path/property/$props/JSON")
            ?: return null
        val p = root.optJSONObject("PropertyTable")?.optJSONArray("Properties")?.optJSONObject(0) ?: return null
        val cid = p.optString("CID")
        return Compound(
            cid = cid, formula = p.optString("MolecularFormula"), weight = p.optString("MolecularWeight"),
            iupac = p.optString("IUPACName"), inchikey = p.optString("InChIKey"),
            xlogp = p.optString("XLogP"), tpsa = p.optString("TPSA"),
            donors = p.optString("HBondDonorCount"), acceptors = p.optString("HBondAcceptorCount"),
            rotatable = p.optString("RotatableBondCount"), heavyAtoms = p.optString("HeavyAtomCount"),
            exactMass = p.optString("ExactMass"), complexity = p.optString("Complexity"),
            url = "https://pubchem.ncbi.nlm.nih.gov/compound/$cid"
        )
    }

    /** 2D structure drawing (PNG bytes) from PubChem. Null = unavailable. */
    fun structurePng(cid: String): ByteArray? = try {
        val conn = URL("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/PNG?image_size=large")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "MENK/0.6 (Android; evidence research app)")
        val bytes = if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
        conn.disconnect()
        bytes
    } catch (e: Exception) {
        null
    }

    // ----- FDA: openFDA -----
    // openFDA answers 404 when nothing matches, so 404 = "not in database", -1 or 5xx = connection problem.

    private fun fda(endpoint: String, field: String, name: String, extra: String): Pair<Int, JSONObject?> {
        val search = enc("$field:\"${name.trim()}\"")
        return request("https://api.fda.gov/drug/$endpoint.json?search=$search$extra")
    }

    private fun failed(code: Int) = code == -1 || code >= 500

    /** Official US drug label. Null = not found or connection problem. */
    fun label(name: String): DrugLabel? {
        for (field in listOf("openfda.generic_name", "openfda.brand_name")) {
            val r = fda("label", field, name, "&limit=1").second
                ?.optJSONArray("results")?.optJSONObject(0) ?: continue
            fun first(vararg keys: String): String =
                keys.firstNotNullOfOrNull { k -> r.optJSONArray(k)?.optString(0)?.takeIf { it.isNotBlank() } } ?: ""
            val o = r.optJSONObject("openfda")
            return DrugLabel(
                brand = o?.optJSONArray("brand_name")?.optString(0) ?: "",
                generic = o?.optJSONArray("generic_name")?.optString(0) ?: "",
                manufacturer = o?.optJSONArray("manufacturer_name")?.optString(0) ?: "",
                uses = clean(first("indications_and_usage", "purpose")).take(700),
                warnings = clean(first("boxed_warning", "warnings", "warnings_and_cautions")).take(700),
                interactions = clean(first("drug_interactions", "ask_doctor_or_pharmacist")).take(900),
                contraindications = clean(first("contraindications", "do_not_use")).take(600)
            )
        }
        return null
    }

    /** Drugs@FDA approvals. total = 0 means not found in FDA's approved-drug database. */
    fun approvals(name: String): FdaApprovals? {
        for (field in listOf("openfda.generic_name", "openfda.brand_name")) {
            val (code, root) = fda("drugsfda", field, name, "&limit=10")
            if (failed(code)) return null
            if (root == null) continue
            val items = root.optJSONArray("results").objects().map { a ->
                val prod = a.optJSONArray("products")?.optJSONObject(0)
                val orig = a.optJSONArray("submissions").objects().firstOrNull {
                    it.optString("submission_type") == "ORIG" && it.optString("submission_status") == "AP"
                }
                Approval(
                    appNo = a.optString("application_number"),
                    sponsor = a.optString("sponsor_name"),
                    brand = prod?.optString("brand_name") ?: "",
                    form = listOf(prod?.optString("dosage_form") ?: "", prod?.optString("route") ?: "")
                        .filter { it.isNotBlank() }.joinToString(", ").lowercase(),
                    marketing = prod?.optString("marketing_status") ?: "",
                    approvedOn = ymd(orig?.optString("submission_status_date") ?: "")
                )
            }
            val total = root.optJSONObject("meta")?.optJSONObject("results")?.optInt("total", items.size) ?: items.size
            return FdaApprovals(total, items)
        }
        return FdaApprovals(0, emptyList())
    }

    /** Products on the US market (NDC directory), counted across all listings. */
    fun market(name: String): Market? {
        fun tally(countField: String, limit: Int): List<Tally>? {
            val (code, root) = fda("ndc", "generic_name", name, "&count=$countField&limit=$limit")
            if (failed(code)) return null
            return root?.optJSONArray("results").objects().map { Tally(it.optString("term"), it.optInt("count")) }
        }
        val categories = tally("marketing_category.exact", 20) ?: return null
        val makers = tally("labeler_name.exact", 8) ?: return null
        val forms = tally("dosage_form.exact", 6) ?: return null
        return Market(categories, makers, forms)
    }

    /** Manufacturing recalls (FDA enforcement reports), newest first. */
    fun recalls(name: String): Recalls? {
        val (code, root) = fda("enforcement", "product_description", name, "&sort=report_date:desc&limit=6")
        if (failed(code)) return null
        if (root == null) return Recalls(0, emptyList())
        val items = root.optJSONArray("results").objects().map { r ->
            Recall(
                firm = r.optString("recalling_firm"),
                reason = clean(r.optString("reason_for_recall")).take(300),
                classification = r.optString("classification"),
                date = ymd(r.optString("report_date")),
                status = r.optString("status")
            )
        }
        val total = root.optJSONObject("meta")?.optJSONObject("results")?.optInt("total", items.size) ?: items.size
        return Recalls(total, items)
    }
}
