# Plan: Android library chuyển đổi PDF và Word

Trạng thái: đề xuất kỹ thuật, cần chốt các câu hỏi ở cuối tài liệu trước khi triển khai production.

## 1. Kết luận khả thi

Có thể xây dựng một Android library không dùng dịch vụ thương mại, không watermark và chỉ dùng thành phần mã nguồn mở. Tuy nhiên, không thể cam kết chuyển đổi hoàn hảo 100% cho mọi tài liệu vì PDF là định dạng mô tả vị trí hiển thị, không phải định dạng lưu cấu trúc logic như Word.

Ước lượng sau đây áp dụng với tài liệu văn phòng thông thường, không có macro, công thức phức tạp, chữ nghệ thuật hoặc font bị cấm nhúng:

| Luồng | Mức khả thi kỹ thuật | Fidelity kỳ vọng | Ghi chú |
|---|---:|---:|---|
| DOCX -> PDF | 90-95% | 85-95% | Tốt nếu thiết bị có đúng font và dùng LibreOffice engine |
| DOC -> PDF | 80-90% | 75-90% | Định dạng binary cũ có nhiều trường hợp đặc biệt |
| PDF text-based -> DOCX, ưu tiên sửa được | 75-85% | 65-85% | Bảng, cột, flow và paragraph phải được suy luận lại |
| PDF text-based -> DOCX, ưu tiên giống hình | 90-95% | 90-98% | Dùng page background/anchored objects; khả năng sửa nội dung giảm đáng kể |
| PDF scan -> DOCX | 60-80% | 50-75% | Bắt buộc OCR; phụ thuộc chất lượng scan và ngôn ngữ |

Xác suất hoàn thành một bản production đáp ứng tiêu chí đã mô tả hiện tại: **khoảng 80%** nếu chấp nhận hai chế độ PDF -> DOCX (`Editable` và `Visual fidelity`) và cho phép đóng gói native engine dung lượng lớn. Nếu yêu cầu vừa pixel-perfect vừa chỉnh sửa/reflow hoàn hảo, xác suất chỉ khoảng **30-40%**.

Các tỷ lệ này là mục tiêu kỹ thuật cần đo bằng bộ corpus thật, không phải bảo đảm cho từng file.

## 2. Kiến trúc đề xuất

Tạo Android library module `:pdf-word-converter`, API Kotlin và engine native C/C++ qua JNI.

```text
Ứng dụng Android
    -> Kotlin API + WorkManager/coroutines + progress/cancel
        -> Input validation / password / temp storage
        -> DOC/DOCX -> PDF: LibreOfficeKit native engine
        -> PDF -> DOCX:
             PDFium parser/render
             -> layout analysis (text runs, images, lines, tables, columns)
             -> OOXML (.docx) writer
             -> optional OCR for scanned pages
        -> validation + visual regression report
```

### API công khai dự kiến

```kotlin
interface DocumentConverter {
    suspend fun pdfToDocx(
        input: Uri,
        output: Uri,
        options: PdfToDocxOptions = PdfToDocxOptions()
    ): ConversionResult

    suspend fun wordToPdf(
        input: Uri,
        output: Uri,
        options: WordToPdfOptions = WordToPdfOptions()
    ): ConversionResult
}
```

API phải hỗ trợ `Uri`/Storage Access Framework, password, timeout, progress, cancel, warning theo trang, giới hạn bộ nhớ và lỗi có kiểu rõ ràng. Không giữ `Activity`/`Context` trong singleton.

### Hai chế độ PDF -> DOCX

1. `EDITABLE`: tái tạo paragraph, run, ảnh, bảng, section, header/footer; cho phép Word reflow. Đây là mode mặc định nhưng không thể giữ tuyệt đối vị trí.
2. `VISUAL`: render mỗi trang làm nền hoặc dùng anchored text/image boxes theo tọa độ. Hình thức gần bản gốc hơn nhưng khó sửa, tìm kiếm và accessibility kém hơn.

Có thể bổ sung `HYBRID`: giữ ảnh/đồ họa phức tạp dưới dạng nền và phủ text thật lên trên. Cần cảnh báo nguy cơ lệch chữ giữa các Word renderer.

## 3. Thành phần mã nguồn mở

| Thành phần | Vai trò | License | Quyết định ban đầu |
|---|---|---|---|
| LibreOfficeKit / LibreOffice core | DOC, DOCX -> PDF; C/C++ native | MPL-2.0 / LGPLv3+ và third-party notices | POC bắt buộc; fidelity cao nhất trong nhóm miễn phí, nhưng build và binary nặng |
| PDFium | parse/render PDF, lấy text/geometry/image | BSD-style và third-party notices | Ưu tiên cho native Android; phải audit toàn bộ `third_party` theo revision đã pin |
| Apache POI XWPF/HWPF | đọc/ghi OOXML/DOC và công cụ phụ trợ | Apache-2.0 | Chỉ dùng chọn lọc; XWPF/HWPF không phải rendering engine hoàn chỉnh |
| Apache PDFBox | reference/parser hoặc tooling test | Apache-2.0 | Dùng cho test/đối chiếu; không tự giải quyết PDF -> DOCX fidelity |
| Tesseract OCR (tùy chọn) | OCR tài liệu scan | Apache-2.0 | Tách thành artifact/feature riêng vì model ngôn ngữ làm tăng dung lượng |

Không chọn MuPDF bản AGPL/commercial, SDK thương mại hoặc dịch vụ online có quota/watermark. Trước release phải tạo SBOM, pin commit/version, lưu LICENSE/NOTICE/source-offer cần thiết và được người phụ trách pháp lý duyệt. “Open source” không đồng nghĩa với không có nghĩa vụ phân phối license/source modifications.

## 4. Chiến lược xử lý

### DOC/DOCX -> PDF

1. Copy `Uri` vào vùng tạm riêng của app, kiểm tra magic bytes, size, encryption và extension.
2. Khởi tạo LibreOfficeKit trong một worker/process riêng để crash native không làm chết UI.
3. Nạp bộ font được phép phân phối và thiết lập font substitution có ghi warning.
4. Export PDF với page size, margin, header/footer, ảnh và embedded fonts phù hợp.
5. Mở lại PDF, kiểm tra số trang, kích thước trang, lỗi parse và tạo thumbnail để regression test.
6. Ghi kết quả ra `Uri` bằng thao tác an toàn; xóa file tạm kể cả khi cancel/crash.

Không dùng Apache POI + FOP làm engine chính: pipeline này không phải Word renderer đầy đủ nên dễ mất floating objects, pagination, field, advanced table và typography.

### PDF -> DOCX

1. Phân loại từng trang: text-based, scan hoặc mixed.
2. Trích glyph/text run kèm bounding box, baseline, font metadata, màu, rotation; render ảnh/vector cần giữ nguyên.
3. Chuẩn hóa Unicode, map/substitute font, gom glyph -> word -> line -> paragraph.
4. Phân tích cột, reading order, list, indentation, page regions, header/footer và footnote.
5. Nhận bảng từ ruling lines + alignment clusters; giữ merged cell và border khi confidence đủ cao.
6. Tạo OOXML trực tiếp với sections, paragraphs/runs, tables, images và anchored objects. Không xuất `.doc`; đầu ra chỉ `.docx` như yêu cầu.
7. Với confidence thấp: fallback theo vùng hoặc theo trang sang ảnh/anchored block thay vì tạo cấu trúc sai.
8. Mở lại DOCX bằng LibreOfficeKit, render ra PDF, so hình với PDF đầu vào và trả `ConversionReport` chứa confidence/warnings.

## 5. Roadmap và tiêu chí hoàn tất

### Phase 0 — Chốt phạm vi và corpus (2-3 ngày)

- Trả lời các câu hỏi xác nhận bên dưới.
- Thu thập tối thiểu 100 file hợp pháp: DOC, DOCX, PDF text, PDF scan; có tiếng Việt, bảng, ảnh, nhiều cột và font thiếu.
- Định nghĩa fidelity bằng SSIM/pixel diff, text accuracy, table structure, page-count delta và manual score.
- Chốt ABI (`arm64-v8a` trước), minSdk hiện tại 24, giới hạn file/RAM/thời gian và ngân sách AAR/APK.

Gate: có corpus đã ẩn dữ liệu nhạy cảm và ngưỡng pass định lượng.

### Phase 1 — Spike kỹ thuật và license (1-2 tuần)

- Tạo `:pdf-word-converter` và sample integration trong `:app`.
- Build LibreOfficeKit tối thiểu cho Android arm64; thử 20 DOC/DOCX đại diện.
- Build/pin PDFium; chứng minh lấy được text geometry, render ảnh và JNI cancel an toàn.
- Đo AAR size, peak RSS, cold-start, thời gian/page và crash rate.
- Lập SBOM + license matrix, đặc biệt các dependency transitively bundled trong native binaries.

Gate: DOCX -> PDF đạt >= 85% manual visual score trên corpus spike, không watermark; legal checklist không có dependency bị cấm.

### Phase 2 — MVP Word -> PDF (2-4 tuần)

- API Kotlin ổn định, worker/process isolation, password, progress/cancel và cleanup.
- DOCX và DOC import; font pack/substitution; output validation.
- Unit, instrumentation, native crash recovery và benchmark trên máy RAM thấp.

Gate: >= 95% file hợp lệ convert không crash; p95 và RAM nằm trong ngưỡng đã chốt; visual score đạt target theo loại file.

### Phase 3 — PDF -> DOCX Visual mode (2-3 tuần)

- Render trang/region, tạo DOCX đúng page size, giữ ảnh và lớp text/search khi khả thi.
- Rotation, crop box, transparency và mixed page.
- Báo rõ mode và giới hạn chỉnh sửa.

Gate: visual score >= 95% cho PDF text-based chuẩn; DOCX mở được trong Word, LibreOffice và Google Docs theo test matrix.

### Phase 4 — PDF -> DOCX Editable mode (6-10 tuần)

- Layout reconstruction, reading order, style inference, image extraction, tables, headers/footers.
- Confidence scoring và fallback từng vùng sang visual representation.
- Vietnamese diacritics, RTL (nếu có), vertical/rotated text và malformed PDF hardening.

Gate: text accuracy >= 98% trên PDF text-based; table structure >= 85% trên tập bảng chuẩn; manual visual score >= 75% trên corpus mục tiêu.

### Phase 5 — OCR tùy chọn (3-5 tuần)

- Tesseract native + language packs tải theo nhu cầu.
- Deskew, denoise, orientation, region OCR và confidence mapping.
- Không quảng bá cùng mức fidelity với PDF text-based.

Gate: character accuracy mục tiêu được chốt riêng theo ngôn ngữ và DPI.

### Phase 6 — Hardening và release (2-4 tuần)

- Fuzz malformed inputs, zip bomb/XXE/path traversal, encrypted file, huge page, memory pressure.
- Test ABI/device/Android version; reproducible native build và symbol files.
- Public API docs, ProGuard/R8 consumer rules, sample app, migration policy, LICENSE/NOTICE/SBOM.
- Publish AAR theo artifact tách rời: `core`, `word-engine`, `pdf-layout`, `ocr-*` để app không phải mang mọi engine.

Tổng ước lượng cho 2 kỹ sư có kinh nghiệm Android + C++: **14-24 tuần** cho production v1 không OCR, thêm **3-5 tuần** cho OCR. Phase 1 là mốc go/no-go; estimate phải cập nhật từ benchmark thật.

## 6. Test matrix tối thiểu

- Android 7 (minSdk 24), 10, 13, 16; arm64-v8a; thiết bị 3/4/8 GB RAM.
- File 1-500 trang; portrait/landscape/mixed; 1-100+ MB; password đúng/sai.
- Latin/tiếng Việt; embedded, subset, missing và custom fonts.
- Paragraph styles, lists, tabs, columns, section breaks, page numbers, header/footer.
- Bảng border/merged/nested; inline/floating images; vector, transparency, rotation.
- PDF scan, mixed scan/text, invalid xref, linearized PDF, form/annotation.
- Mở output bằng Microsoft Word desktop/mobile, LibreOffice và Google Docs; renderer khác nhau có thể cho pagination khác nhau.

## 7. Rủi ro chính và cách giảm

- **Kích thước và RAM của LibreOffice:** strip module không cần thiết, split artifacts/ABI, chạy process riêng; Phase 1 có gate dung lượng rõ ràng.
- **Thiếu font:** không thể nhúng font không có quyền; cung cấp font pack OFL/Apache và trả warning substitution.
- **PDF không có semantic structure:** dùng confidence + hybrid fallback, không đoán bảng/paragraph khi độ tin cậy thấp.
- **DOC cũ:** ưu tiên LibreOffice import; không cam kết macro, ActiveX, OLE embedded object hoặc tracked changes hoàn hảo.
- **Khác biệt renderer:** golden test trên nhiều viewer; định nghĩa “giữ giao diện” bằng metric thay vì cảm nhận.
- **Bảo mật native parser:** sandbox process, limit tài nguyên, fuzz và thường xuyên cập nhật revision đã audit.
- **License transitively bundled:** SBOM và audit lại mỗi lần nâng version; không nhận dependency chỉ dựa vào tên license của project cha.

## 8. Câu hỏi cần xác nhận trước Phase 1

1. Bắt buộc xử lý **100% offline/on-device**, hay cho phép một conversion server tự quản lý dùng LibreOffice? Server giúp giảm đáng kể AAR size/RAM và dễ cập nhật bảo mật.
2. Với PDF -> DOCX, ưu tiên **giống hình** hay **chỉnh sửa/reflow tốt**? Có chấp nhận cung cấp hai mode không?
3. “Tương đối hoàn hảo” được chấp nhận ở ngưỡng nào: ví dụ >= 90% visual score và không lệch quá 1 trang/100 trang?
4. Có cần hỗ trợ PDF scan/OCR ngay v1 không? Ngôn ngữ chỉ Việt/Anh hay thêm ngôn ngữ khác?
5. Kích thước tăng tối đa cho app/AAR là bao nhiêu? Có thể tải engine/font/OCR pack động sau cài đặt không?
6. ABI cần hỗ trợ: chỉ `arm64-v8a`, hay cả `armeabi-v7a`, `x86_64`?
7. Giới hạn file thực tế: dung lượng, số trang, thời gian chuyển đổi và RAM tối đa?
8. Có cần hỗ trợ file có password, chữ ký số, PDF forms, annotation, macro, tracked changes, equation, chart, OLE embedded object không?
9. Output DOCX phải mở đúng trên Microsoft Word phiên bản nào; có bắt buộc Google Docs/LibreOffice không?
10. Công ty có chấp nhận nghĩa vụ của MPL-2.0/LGPLv3+ và quy trình cung cấp notices/source modifications không? Cần legal duyệt trước khi ship.
11. Có bộ file thật đã được phép dùng làm corpus kiểm thử không? Nếu có, định dạng và tỷ trọng từng nhóm là gì?
12. Library chỉ dùng nội bộ một app hay sẽ publish SDK cho bên thứ ba? Điều này ảnh hưởng API stability, ABI, tài liệu và compliance.

## 9. Quyết định đề xuất

Tiến hành Phase 0 và Phase 1 trước, chưa cam kết toàn roadmap. Ưu tiên `DOC/DOCX -> PDF` và `PDF -> DOCX Visual` để có kết quả sớm; chỉ đầu tư Editable mode sau khi corpus chứng minh layout reconstruction đạt ngưỡng. Nếu giới hạn AAR/RAM quá chặt, chuyển LibreOffice conversion sang server tự quản lý là phương án thực tế hơn.

## 10. Nguồn kỹ thuật chính

- LibreOfficeKit documentation: https://docs.libreoffice.org/libreofficekit.html
- LibreOffice core/build baseline: https://github.com/LibreOffice/core
- LibreOffice licensing overview: https://www.libreoffice.org/about-us/licenses/
- PDFium source and license: https://pdfium.googlesource.com/pdfium/
- Apache PDFBox: https://pdfbox.apache.org/
- Apache POI Word support and limitations: https://poi.apache.org/components/document/index.html

