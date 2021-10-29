package io.github.deltacv.easyvision.node

import com.github.serivesmejia.eocvsim.util.Log
import io.github.classgraph.ClassGraph
import kotlinx.coroutines.*

typealias CategorizedNodes = Map<Category, MutableList<Class<out Node<*>>>>

object NodeScanner {

    val TAG = "NodeScanner"

    val ignoredPackages = arrayOf(
        "java",
        "org.opencv",
        "imgui",
        "io.github.classgraph",
        "org.lwjgl"
    )

    var result: CategorizedNodes? = null
        private set

    @Suppress("UNCHECKED_CAST") //shut
    fun scan(useCache: Boolean = true): CategorizedNodes {
        if(result != null && useCache) return result!!

        val nodes = mutableMapOf<Category, MutableList<Class<out Node<*>>>>()

        Log.info(TAG, "Scanning for nodes...")

        val classGraph = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .rejectPackages(*ignoredPackages)

        val scanResult = classGraph.scan()
        val nodeClasses = scanResult.getClassesWithAnnotation(RegisterNode::class.java.name)

        for(nodeClass in nodeClasses) {
            val clazz = Class.forName(nodeClass.name)

            val regAnnotation = clazz.getDeclaredAnnotation(RegisterNode::class.java)

            if(hasSuperclass(clazz, Node::class.java)) {
                val nodeClazz = clazz as Class<out Node<*>>

                var list = nodes[regAnnotation.category]

                if(list == null) {
                    list = mutableListOf(nodeClazz)
                    nodes[regAnnotation.category] = list
                } else {
                    list.add(nodeClazz)
                }

                Log.info(TAG, "Found node ${nodeClazz.typeName}")
            }
        }

        Log.info(TAG, "Found ${nodeClasses.size} nodes")
        Log.blank()

        result = nodes
        return nodes
    }

    private var job: Job? = null

    val hasFinishedAsyncScan get() = job == null && result != null

    @OptIn(DelicateCoroutinesApi::class)
    fun startAsyncScan() {
        job = GlobalScope.launch(Dispatchers.IO) {
            scan()
            job = null
        }
    }

    fun waitAsyncScan(): CategorizedNodes {
        if(job != null) {
            runBlocking {
                job!!.join()
            }
        }

        return result!!
    }

}

fun hasSuperclass(clazz: Class<*>, superClass: Class<*>): Boolean {
    return try {
        clazz.asSubclass(superClass)
        true
    } catch (ex: ClassCastException) {
        false
    }
}