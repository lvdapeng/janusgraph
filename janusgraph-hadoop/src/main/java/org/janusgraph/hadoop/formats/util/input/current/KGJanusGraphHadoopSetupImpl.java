// Copyright 2017 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.janusgraph.hadoop.formats.util.input.current;

import com.google.common.base.Preconditions;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.janusgraph.core.JanusGraphVertex;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.keycolumnvalue.SliceQuery;
import org.janusgraph.diskstorage.util.StaticArrayBuffer;
import org.janusgraph.graphdb.database.RelationReader;
import org.janusgraph.graphdb.database.StandardJanusGraph;
import org.janusgraph.graphdb.idmanagement.IDManager;
import org.janusgraph.graphdb.internal.JanusGraphSchemaCategory;
import org.janusgraph.graphdb.query.QueryUtil;
import org.janusgraph.graphdb.transaction.StandardJanusGraphTx;
import org.janusgraph.graphdb.transaction.StandardTransactionBuilder;
import org.janusgraph.graphdb.types.TypeDefinitionCategory;
import org.janusgraph.graphdb.types.TypeDefinitionMap;
import org.janusgraph.graphdb.types.TypeInspector;
import org.janusgraph.graphdb.types.system.BaseKey;
import org.janusgraph.graphdb.types.system.BaseLabel;
import org.janusgraph.graphdb.types.vertices.JanusGraphSchemaVertex;
import org.janusgraph.hadoop.formats.util.input.JanusGraphHadoopSetup;
import org.janusgraph.hadoop.formats.util.input.SystemTypeInspector;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class KGJanusGraphHadoopSetupImpl implements JanusGraphHadoopSetup {

    private static final StaticBuffer DEFAULT_COLUMN = StaticArrayBuffer.of(new byte[0]);
    public static final SliceQuery DEFAULT_SLICE_QUERY = new SliceQuery(DEFAULT_COLUMN, DEFAULT_COLUMN);

    private final StandardJanusGraph graph;
    private final StandardJanusGraphTx tx;

    public KGJanusGraphHadoopSetupImpl(StandardJanusGraph janusGraph) {
        graph = janusGraph;
        tx = (StandardJanusGraphTx)graph.buildTransaction().readOnly()
        .dirtyVertexSize(0)
        .checkInternalVertexExistence(false)
        .vertexCacheSize(0).start();
    }

    public StandardJanusGraphTx startTransaction(StandardJanusGraph graph) {
        StandardTransactionBuilder txb = graph.buildTransaction().readOnly();
        txb.setPreloadedData(true);
        txb.checkInternalVertexExistence(false);
        txb.dirtyVertexSize(0);
        txb.vertexCacheSize(0);
        return (StandardJanusGraphTx)txb.start();
    }

    @Override
    public TypeInspector getTypeInspector() {
        //Pre-load schema
        for (JanusGraphSchemaCategory sc : JanusGraphSchemaCategory.values()) {
            for (JanusGraphVertex k : QueryUtil.getVertices(tx, BaseKey.SchemaCategory, sc)) {
                assert k instanceof JanusGraphSchemaVertex;
                JanusGraphSchemaVertex s = (JanusGraphSchemaVertex)k;
                if (sc.hasName()) {
                    String name = s.name();
                    Preconditions.checkNotNull(name);
                }
                TypeDefinitionMap dm = s.getDefinition();
                Preconditions.checkNotNull(dm);
                s.getRelated(TypeDefinitionCategory.TYPE_MODIFIER, Direction.OUT);
                s.getRelated(TypeDefinitionCategory.TYPE_MODIFIER, Direction.IN);
            }
        }
        return tx;
    }

    @Override
    public SystemTypeInspector getSystemTypeInspector() {
        return new SystemTypeInspector() {
            @Override
            public boolean isSystemType(long typeId) {
                return IDManager.isSystemRelationTypeId(typeId);
            }

            @Override
            public boolean isVertexExistsSystemType(long typeId) {
                return typeId == BaseKey.VertexExists.longId();
            }

            @Override
            public boolean isVertexLabelSystemType(long typeId) {
                return typeId == BaseLabel.VertexLabelEdge.longId();
            }

            @Override
            public boolean isTypeSystemType(long typeId) {
                return typeId == BaseKey.SchemaCategory.longId() ||
                        typeId == BaseKey.SchemaDefinitionProperty.longId() ||
                        typeId == BaseKey.SchemaDefinitionDesc.longId() ||
                        typeId == BaseKey.SchemaName.longId() ||
                        typeId == BaseLabel.SchemaDefinitionEdge.longId();
            }
        };
    }

    @Override
    public IDManager getIDManager() {
        return graph.getIDManager();
    }

    @Override
    public RelationReader getRelationReader() {
        return graph.getEdgeSerializer();
    }

    @Override
    /**
     * 只关闭事务，而不关闭graph图库连接
     */
    public void close() {
        tx.rollback();
        if (tx.isOpen()) {
            tx.close();
        }
    }

    @Override
    public boolean getFilterPartitionedVertices() {
        return true;
    }
}
