# IP-5 Advanced Algorithmic Proposal: STGH

## 1. SOTA Synthesis & Architectural Breakdown

**The Baseline (SWELFT)**
The Stability-Weighted Elastic Layout Fingerprinting with Transfer Stencils (SWELFT) framework optimizes extraction by fingerprinting the raw layout of financial tables. It rasterizes the coordinate space mapping text bounding-boxes onto a grid, converts this to a stability-weighted vector, and maps the layout utilizing Locality-Sensitive Hashing (LSH) to identify identical prior templates. If a match occurs, it bypasses expensive Machine Learning extraction in favor of zero-shot stencil mapping.

**Architectural Mapping**
Currently, when a PDF processes, spatial bounding boxes are extracted. SWELFT drops them into geometric histogram buckets. The resultant array undergoes MinHash signature generation. This signature queried against an ElasticSearch nearest-neighbor index dynamically retrieves the previous manual field bindings, projecting them onto the new document via spatial affine transformations.

## 2. Critical Gap Analysis (Theory & Practice)

**Theoretical Blindspots**
Raster grid hashing strictly optimizes geometric density, making it fundamentally "Semantically Agnostic." Consider an adversarial semantic shift (or generic reporting change over fiscal years): A company swaps the precise coordinates of the generic 'Operating Expenses' table with the 'Assets' table. The bounding box dimensions, grid densities, and structural layout appear identical locally. SWELFT hashes will trigger an exact spatial match, pulling the old extraction stencil and subsequently mapping liabilities values into the core asset pipeline without alerting the system, triggering an uncatchable hallucination error cascade.

**Engineering Bottlenecks**
Calculating multi-dimensional entropy histograms $H(x)$ globally across an entire document and running high-resolution affine transformation grids on CPUs introduces unpredictable memory exhaustion vulnerabilities (OOM crashes) during the MinHash generation for exceptionally large documents (e.g., 500-page prospectuses). 

## 3. The Algorithmic Proposal (The Breakthrough)

**Algorithm Name:** STGH (Semantic-Topological Graph Hashing)

**Core Mechanism**
STGH forces layout memory to recognize language natively. Before fingerprinting the document layout, STGH embeds explicit textual token classes directly into the geometric nodes utilizing a lightweight Graph Convolutional Network (GCN).

1. **Semantic Topological Classification:** Every bounding box receives an inference label reflecting simplistic structural semantics (e.g., `Header`, `Currency`, `Paragraph`, `Date`).
2. **Class-Weighted Adjacency Hashing:** Instead of rasterizing a raw dense point grid, STGH constructs a sparse directional Graph. Each node acts as an edge to its spatial neighbors, but the edge weights combine distance + Semantic Class type. 
3. **SimHash Sub-Graph Compression:** The Graph structures are passed via a randomized SimHash permutation schema. Now, if the table structure matches but a massive structural classification swaps (a text block takes the place of a number block), the hash rigorously diverges, canceling the LSH elastic match and mathematically preventing catastrophic stencil hallucinatory mappings.

**Big-O Trade-offs**
*   **Original SWELFT:** MinHash Grid Rasterization scales geometrically based on chosen grid density bounds. $O(D_x \times D_y)$.
*   **Proposed STGH:** Sparse Spatial $k$-NN Graph construction bounded by $O(N \log N)$ where $N$ is text elements. GCN inference takes strictly $O(|V| + |E|)$, completely stripping dense grid rasterization memory leaks.

## 4. Technical Implementation Details

**Core Pseudocode (Python / PyTorch / NetworkX)**

```python
import networkx as nx
import torch
import torch.nn.functional as F
from torch_geometric.nn import GCNConv

class SemanticNodeClassifier(torch.nn.Module):
    # Ultra-lightweight pre-trained layout semantics
    def __init__(self, in_features, classes=4):
        super().__init__()
        self.conv1 = GCNConv(in_features, 16)
        self.conv2 = GCNConv(16, classes) # Classes: Val, Text, Header, Date
        
    def forward(self, node_spatial_features, edge_index):
        x = F.relu(self.conv1(node_spatial_features, edge_index))
        return F.log_softmax(self.conv2(x, edge_index), dim=1)

def compute_STGH_hash(spatial_components, pretrained_gcn):
    # 1. Spatially defined k-NN edges
    graph_edges = build_k_nearest_neighbor_edges(spatial_components, k=5)
    node_features = extract_box_dimensions(spatial_components)
    
    # 2. Assign base ontology classes without OCR text extraction overhead
    predicted_classes = pretrained_gcn(node_features, graph_edges).argmax(dim=1)
    
    # 3. Build Semantic-Aware Random Projection (SimHash)
    hash_bits = []
    vector_accumulation_layer = torch.zeros(HASH_DIMENSION)
    
    for i in range(len(spatial_components)):
        # Node hash seeded uniquely by its predicted semantic class
        seeded_projector = generate_pseudo_random_vector(seed=predicted_classes[i])
        weight_magnitude = node_features[i].area
        # Inject positive or negative momentum linearly
        vector_accumulation_layer += seeded_projector * weight_magnitude
        
    # Compress into compact 256-bit signature representing Geometry + Semantics
    hash_signature = (vector_accumulation_layer > 0).int()
    return hash_signature

def LSH_Stencil_Engine(document_graph):
    doc_hash = compute_STGH_hash(document_graph.nodes, global_gcn)
    
    # Secure Elastic nearest neighbor search
    matches = elastic_search_hamming_distance(doc_hash, threshold=0.98)
    if not matches:
        return trigger_vlm_fallback()
    return map_elastic_stencils(matches[0])
```

**Production Architecture Integration**
The STGH framework shifts the computational burden away from heavy OCR text analysis purely to spatial/class boundaries. The GCN inference runs in $\sim 15ms$ natively via an ONNX Runtime embedded directly on the application server (no GPU routing overhead logic needed). Stencil hashes are persisted as INT vectors inside Redis/ElasticSearch executing sub-millisecond bitwise Hamming comparisons natively.
