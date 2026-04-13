import { useState } from 'react';
import { ScanResults } from '../components/scanner/ScanResults';
import { ProductAnalysis } from '../components/scanner/ProductAnalysis';

/**
 * Scanner page — run market scans and analyze individual products.
 */
const ScannerPage = () => {
  const [selectedProduct, setSelectedProduct] = useState<string | null>(null);

  return (
    <div className="d-flex flex-column gap-4">
      <h1 className="h4 fw-bold">Market Scanner</h1>

      {selectedProduct && (
        <ProductAnalysis
          productId={selectedProduct}
          onClose={() => setSelectedProduct(null)}
        />
      )}

      <ScanResults onAnalyze={setSelectedProduct} />
    </div>
  );
};

export default ScannerPage;
