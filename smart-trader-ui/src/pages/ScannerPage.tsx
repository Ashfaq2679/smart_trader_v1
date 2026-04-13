import { useState } from 'react';
import { ScanResults } from '../components/scanner/ScanResults';
import { ProductAnalysis } from '../components/scanner/ProductAnalysis';

/**
 * Scanner page — run market scans and analyze individual products.
 */
const ScannerPage = () => {
  const [selectedProduct, setSelectedProduct] = useState<string | null>(null);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Market Scanner</h1>

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
