# Android PDF/Word converter — kế hoạch và trạng thái

## Kết luận khả thi

Library offline, không watermark và không cần dịch vụ trả phí là khả thi. Tuy nhiên PDF không lưu cấu trúc logic như Word, vì vậy không thể đồng thời cam kết 100% editable và pixel-perfect cho mọi tài liệu.

Ước lượng trên tài liệu văn phòng thông thường:

| Luồng | Mức kỳ vọng hợp lý |
|---|---:|
| PDF → DOCX Visual | 90–98% giống hình; nội dung không editable |
| PDF text → DOCX Editable | 65–85% fidelity |
| PDF scan → DOCX Editable bằng OCR | 50–75% fidelity |
| DOCX → PDF bằng renderer hiện tại | 60–80% fidelity |
| DOC → PDF bằng renderer hiện tại | 45–70% fidelity |

Muốn Word → PDF đạt khoảng 85–95% trên tài liệu phức tạp cần tích hợp LibreOfficeKit. LibreOffice là phần mềm tự do, không watermark, nhưng bản Android native lớn và phải tuân thủ MPL-2.0/LGPLv3+ cùng third-party notices.

## Phạm vi đã triển khai

- Module Android library `:pdf-word-converter`, API Kotlin dùng `Uri`, coroutine, progress, cancel và lỗi có kiểu.
- PDF → DOCX `VISUAL`: render từng trang thành ảnh, giữ kích thước trang và ưu tiên giống giao diện.
- PDF → DOCX `EDITABLE`: lấy glyph, font, size, bold/italic và tọa độ từ PDF; OCR ML Kit bundled/offline cho trang scan; suy luận paragraph, hai cột và bảng cơ bản; lớp graphics toàn trang giữ raster image, vector/path, shape và paint đúng vị trí phía sau text editable.
- DOCX → PDF: paragraph/run, font cơ bản, bold/italic, ảnh inline, bảng cơ bản và phân trang.
- DOC 97–2003 → PDF: paragraph/run và style chữ cơ bản.
- Sample app chọn hai chiều chuyển đổi và hai mode PDF → DOCX.
- Unit test và instrumentation test trên thiết bị thật cho cả hai chiều, gồm kiểm tra PDF → DOCX giữ chữ editable và raster image.

## Giới hạn hiện tại

- PDF Editable chưa tái tạo hoàn hảo vector, clipping, chữ xoay/skew, header/footer, footnote, merged/nested table và reading order phức tạp.
- ML Kit OCR nhận chữ offline nhưng độ chính xác phụ thuộc DPI, độ nghiêng, nhiễu và chất lượng scan.
- Word renderer hiện tại không phải Microsoft Word/LibreOffice engine: floating object, chart, field, equation, section, header/footer, tracked changes và pagination phức tạp có thể lệch.
- Macro, OLE, password/encryption, chữ ký số, form và annotation chưa thuộc phạm vi v1.
- Input Word giới hạn 100 MB; output tối đa 300 trang; minSdk 26.

## Kiến trúc hiện tại

```text
AndroidDocumentConverter
├── PDF → DOCX
│   ├── PdfRenderer: render Visual/OCR
│   ├── PdfBox-Android: glyph + raster image
│   ├── ML Kit bundled: OCR offline
│   └── OOXML writer: paragraph/table/anchored image
└── DOC/DOCX → PDF
    ├── Apache POI XWPF/HWPF: parse Word
    └── Android PdfDocument: layout + render PDF
```

## Tiêu chí hoàn tất production

1. Chạy corpus nội bộ đã ẩn dữ liệu nhạy cảm: PDF text/scan/mixed, DOC/DOCX, bảng, ảnh, tiếng Việt và nhiều cột.
2. Đo text accuracy, table structure, page-count delta, visual diff, thời gian và peak RAM theo từng nhóm.
3. Chốt ngưỡng pass thực tế thay vì dùng một tỷ lệ chung cho mọi file.
4. Fuzz malformed PDF/ZIP/OLE, kiểm thử file mã hóa, memory pressure và cancellation.
5. Nếu fidelity Word → PDF hiện tại không đạt corpus, tích hợp LibreOfficeKit trong process riêng.
6. Hoàn tất legal review, SBOM, LICENSE/NOTICE và pin version trước khi phân phối.

## Quyết định đã xác nhận

- Xử lý 100% offline/on-device.
- Có cả `EDITABLE` và `VISUAL`, ưu tiên `EDITABLE`.
- OCR dùng ML Kit bundled.
- App nội bộ, chưa cần password/macro/OLE/chữ ký số ở thời điểm này.
- Chấp nhận xem xét LibreOffice và nghĩa vụ giấy phép nếu cần nâng fidelity Word → PDF.

## Thư viện

| Thành phần | Phiên bản | Vai trò | License |
|---|---:|---|---|
| PdfBox-Android | 2.0.27.0 | parse text/image PDF | Apache-2.0 |
| Apache POI | 5.5.1 | parse DOC/DOCX | Apache-2.0 |
| ML Kit Text Recognition | 16.0.1 | OCR bundled/offline | Google ML Kit terms |
| Kotlin Coroutines | 1.10.2 | async/cancellation | Apache-2.0 |

Không dùng MuPDF AGPL/commercial, SDK trả phí hoặc thành phần tạo watermark.
