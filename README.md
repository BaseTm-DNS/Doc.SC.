# Doc.SC - Document Scanner & Security

Doc.SC is a modern Android application built with Kotlin and Jetpack Compose for document scanning, enhancement, multi-page organization, secure vault storage, and PDF export.

## Features

- **Document Scanner & Camera Capture**: Capture documents, receipts, IDs, and contracts using the device camera or zero-permission photo picker.
- **Image Filters & Enhancement**:
  - Original
  - B&W Clean (crisp binarized contrast for documents and receipts)
  - Grayscale
  - Enhanced / Magic Color (sharpened contrast and saturation)
  - Warm / Sepia
- **Page Management**: Rotate pages by 90°, manage multi-page document bundles, and inspect page counts.
- **PDF Export & Sharing**: Export documents directly into standard multi-page PDF documents with headers, timestamps, and page numbers, and share via Android Sharesheet.
- **Secure Encrypted Vault**: PIN-locked vault (Default PIN: `1234`) for sensitive legal contracts, IDs, and financial records.
- **Digital Integrity Fingerprint**: Generates SHA-256 cryptographic hashes for each document scan to verify tamper resistance.
- **DNS & Host Security Inspector**: Dedicated diagnostic tool honoring the BaseTm-DNS foundation for inspecting domain records (A, AAAA, MX, TXT, CNAME, NS) and measuring network latency.
