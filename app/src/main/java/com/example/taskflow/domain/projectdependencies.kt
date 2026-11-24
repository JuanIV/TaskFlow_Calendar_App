package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity

// Ids de todos los ancestros de un nodo
fun ancestorIds(nodes: List<ProjectNodeEntity>, id: Long): Set<Long> {
    val byId = nodes.associateBy { it.id }
    val out = HashSet<Long>()
    var parent: Long? = byId[id]?.parentId
    while (parent != null) {
        if (!out.add(parent)) break
        parent = byId[parent]?.parentId
    }
    return out
}

// Nodos que pueden ser predecesores de successorId: no son el, ni sus descendientes, ni sus ancestros
fun dependencyCandidates(nodes: List<ProjectNodeEntity>, successorId: Long): List<ProjectNodeEntity> {
    val excluded = subtreeIds(nodes, successorId) + ancestorIds(nodes, successorId)
    return nodes
        .filter { it.id !in excluded }
        .sortedWith(compareBy<ProjectNodeEntity>({ it.title.lowercase() }, { it.id }))
}
