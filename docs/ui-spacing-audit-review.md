# Review khoảng cách UI và countdown — 31/10/2026

## Lỗi và sửa đổi

- **Waiting Room:** dòng tùy chọn mới bị vẽ vào vùng phía trên Ready khi panel
  vẫn cao 580. `PvpRoomPanel` dùng chiều cao 620 riêng cho Waiting Room, nâng
  header/mã phòng/danh sách người chơi, giữ nút và hit target đồng bộ khi resize.
  Mode/số tàu/nhãn mã gộp một dòng, tách mã khỏi nhãn; các dòng luật/tùy chọn có
  khoảng trống rõ với Ready. Kiểm tra cả một người, hai người và status hai dòng.
- **Countdown:** `BattleHud` đổi chiều ngang nét từ 360 về 540; khoảng cách trên–
  dưới từ 134 còn 90, xấp xỉ hai phần ba. START từ size 70 còn 46; số từ 112 còn
  76. Vẫn dùng nét trắng liền thon hai đầu, không đổi thời gian server.
- **AccountPanel/MainMenu:** bỏ subtitle trang trí sát tiêu đề; tách username
  Profile khỏi tên hiển thị.
- **SettingsPanel:** bỏ câu dẫn sát tiêu đề, tăng khoảng cách nhãn Display và
  khoảng cách dòng Controls từ 22.5 lên 28. Bỏ hướng dẫn thu gọn chat đã hết tác dụng.
- **FriendsPanel:** tên, username và trạng thái theo cột trên cùng một hàng;
  lời mời vẫn hiển thị thời điểm. Bỏ subtitle thừa; không giảm font để nhét chữ.
- **LeaderboardPanel:** tên/username theo hai cột, giữ sort/pagination và điều
  kiện số trận. Bỏ dòng mô tả phụ sát footer; không đổi tính toán thống kê.
- **GameOverOverlay:** tăng chiều cao 236 lên 280; tách title, nguyên nhân, người
  thắng và trạng thái rematch. Không đổi Victory/Defeat hoặc hành động các nút.

Không thay background, scene/castle/board/ships, camera, lighting, minimap,
backend, shared/protocol hoặc gameplay trong lượt sửa này. Các thay đổi từ lượt
trước đang ở working tree được giữ nguyên; không tự tạo commit.

## Quy trình kiểm tra

`TypographyRenderChecks` mở rộng từ 27 lên 49 trạng thái: đăng nhập/đăng ký có và
không có dữ liệu, lỗi đăng nhập, cấu hình server, Profile, mất/kết nối lại, Menu,
Settings windowed/fullscreen, cấu hình phòng, nhập mã, ba trạng thái Waiting,
Friends loading/list/search/incoming/outgoing/empty/error/xóa bạn, Leaderboard
list/empty/error, chat, lời mời pending/sent/error/expired, placement/foldout,
Battle/report ẩn, 3–2–1/START, Victory/Defeat/rematch và Leave confirmation.

- Tạo contact sheet và xem toàn bộ bộ ảnh, sau đó mở ảnh đầy đủ ở những màn có
  vấn đề. Kiểm tra số đo glyph để bổ sung cho xem ảnh, không chỉ dựa compile.
- Thêm assertion dòng tùy chọn Waiting phải nằm trên Ready với khoảng trống.
- Thêm quét các BoundingBox của BitmapText đang hiển thị; báo cáo cặp chồng chữ
  tại `layout-findings.tsv`. Chuyển báo cáo thành assertion: có chồng chữ vượt
  ngưỡng 2 pixel trong fixture thì task render fail.
- Ở vòng đầu phát hiện các vị trí header/row/result sít nhau. Vòng tiếp theo
  phát hiện thêm Controls và footer Leaderboard ở 1920×1080; đã sửa thay vì bỏ
  qua assertion hoặc tăng ngưỡng để cho qua.

## Kết quả cuối

- **49 trạng thái × 4 kích thước = 196 ảnh render**: 1280×800, 960×600,
  1280×720 và 1920×1080. Bao gồm cả tỉ lệ 16:10 và 16:9.
- `:frontend:checkTypographyRendering` đạt ở cả bốn kích thước. Cả bốn file
  `build/font-review/<resolution>/layout-findings.tsv` đều rỗng.
- `:frontend:checkUiFeedback` đạt, giữ hit target và các thao tác nhập liệu đang có.
- Chạy lại **build toàn bộ** sau các sửa cuối: đạt. Log build cuối ở
  `build/ui-audit-final-build.log`; log render cuối ở `build/ui-spacing-verified.log`.
  `build/ui-spacing-final.log` giữ lần phát hiện lỗi Controls/footer ở 1920×1080.
- Đã xem toàn bộ 49 trạng thái cuối qua `build/ui-audit-sheets/final-sheet-1.png`
  tới `final-sheet-9.png`; mở ảnh đầy đủ của Waiting Room, status dài, START,
  Friends, Leaderboard và Game Over để kiểm tra khoảng cách và khả năng đọc.

Đây là kiểm tra render OpenGL trên Windows với dữ liệu fixture và kiểm tra tự
động tọa độ chữ, không phải chạy lại toàn bộ PvP bằng hai client hoặc xác minh
native macOS. Nó không bảo đảm mọi chuỗi dữ liệu tùy ý đều đúng; bộ ảnh hiện tại
đã thêm tên dài, dấu tiếng Việt và thông báo hai dòng để phủ các lỗi vừa gặp.

Đóng client cũ rồi chạy `./gradlew.bat :frontend:run`. Không cần đổi database hoặc
khởi động lại backend chỉ cho các sửa spacing/countdown của lượt này.
