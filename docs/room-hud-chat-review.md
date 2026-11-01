# Review Room Setup, HUD và chat — 30/10/2026

## Phạm vi và thay đổi

### Tạo phòng và nhập mã

- `pvp/PvpRoomPanel`: Create Room mở layout hai cột, tự căn giữa theo kích thước
  cửa sổ. Bên trái tóm tắt đội hình và các preset; bên phải là lựa chọn hiển thị
  đồng thời, không cần bấm tuần tự để đoán các mode có sẵn.
- Mode: Basic / Fleet Designer. Số tàu: 3 / 4 / 5. Luật bắn: Alternate Shots /
  Hit–Fire Again. Viền và nền làm nổi lựa chọn đang dùng, kèm mô tả từng luật.
- Preset Duel = Basic, 3 tàu, luân phiên; Tactical = Basic, 5 tàu, trúng bắn tiếp;
  Creative = Designer, 5 tàu, trúng bắn tiếp. Đây là tổ hợp luật đã có, không thêm
  mode hoặc setting giả. Có thể chỉnh từng lựa chọn sau khi chọn preset.
- `pvp/PvpController`: chuyển các action lựa chọn mới vào cấu hình phòng hiện có;
  chỉ gửi CreateRoomRequest khi xác nhận. Backend/protocol giữ nguyên.
- `ui/ClipboardService`, `pvp/RoomCodeInput`: đọc clipboard bất đồng bộ, callback
  về input thread; hỗ trợ Ctrl+V, Cmd+V và Shift+Insert. Mã được chuẩn hóa, thay
  toàn bộ ô nhập; dữ liệu sai giữ mã hiện tại và báo lỗi, không tự gửi Join.

### HUD trận và đặt tàu

- `game/BattleHud`: nhãn Ready/Deploying và dấu check được đưa lên trước avatar.
  Avatar của mình lớn hơn; banner dài hơn, giữ chung tâm với minimap.
- `ui/NavalHudSkin`: mesh nét trắng liền, dày ở giữa và thon về hai đầu. Countdown
  dùng hai nét này với alpha 0.58 và chữ lớn, bỏ hộp nền cũ. Luật 3 giây do server
  quyết định được giữ nguyên.
- Thông báo lượt/mục tiêu chuyển lên phía trên, chữ lớn, tự tắt sau 3.2 giây.
  `GameController` chỉ cập nhật thời gian hiển thị HUD, không thay đồng hồ server.
- `game/PlacementHud`: khung hướng dẫn/random dịch sang phải; hai nút xem bàn/rời
  trận dùng một thanh thao tác cân giữa với padding và màu đồng bộ.
- `game/OwnBoardMinimap`: thêm rail lõm, bracket góc, điểm kim loại và chi tiết hình
  thoi ở mép. Không đổi dữ liệu, hướng xoay hay material mặt lưới.
- `ui/BattleSidebarLayout`: tăng chiều dài banner và khoảng cách minimap, bỏ liên
  kết vị trí chat với cột minimap vì chat nay nằm ở góc màn hình.

### Chat

- `chat/ChatPanel`: trong Battle dùng log mờ ở góc trái dưới, ô nhập nằm sát đáy,
  không dùng header CHAT lớn hoặc nút Send. Manrope hỗ trợ tiếng Việt; tên mình
  cyan, tên đối thủ cam nhạt, nội dung ivory. Danh tính lấy từ account đã xác thực.
- Enter mở nhập; Enter tiếp theo gửi và bỏ focus trong Battle. Tin mới nối lên
  danh sách, tự theo cuối nếu đang ở cuối; khi đọc tin cũ không kéo scroll anchor.
  Tin mới mở log nếu đang thu gọn. Scrollbar và cuộn chuột vẫn dùng input hiện có.
- Waiting Room giữ khung chat cũ. Read-only sau kết thúc, giới hạn nội dung và
  ngữ cảnh theo ván vẫn dùng backend/protocol có sẵn.
- `ChatController` chỉ cung cấp account ID cho phân màu; `ChatInput` không cho
  thao tác gõ/cuộn rơi xuống placement, bắn hoặc camera.

## Bảo toàn và kiểm tra

Không sửa file background/scene, castle, ocean, model tàu, board 3D, camera,
lighting, backend, shared, database hoặc protocol. Không thêm asset ảnh/âm thanh.
Các file `.idea` của người dùng không thuộc thay đổi này.

### Kiểm thử thực tế

- `gradlew build :frontend:checkCombatPresentation :frontend:checkTypographyRendering`
  đạt. Build cả ba module; có kiểm tra luật/timer backend, Room Setup gửi yêu cầu
  bằng TCP với scripted server và các kiểm tra UI hiện có. PostgreSQL checks
  không bật trong lượt này.
- Qua ảnh render thấy nhãn preset/số tàu còn sát nút; đã tăng khoảng cách và thu
  log chat để tránh chiếm thêm diện tích bàn chơi.
- Kiểm tra cuối `:frontend:checkUiFeedback :frontend:checkUiInteractions
  :frontend:checkTypographyRendering` đạt. Room option hit targets ở 960×600,
  1280×800, 1920×1080; preset và mã paste hợp lệ/sai; Ctrl+V đi qua listener và
  callback bất đồng bộ (clipboard được inject trong test, không ghi đè clipboard
  người dùng). Có kiểm tra font/caret/Backspace hiện có.
- GLFW/OpenGL thật: menu click sau đổi windowed/fullscreen/maximize; kéo scrollbar,
  cuộn chuột và giữ scroll anchor khi có tin mới đều đạt. Không thay đổi độ nhạy
  hoặc art direction của camera.
- Render 26 trạng thái ở 1440×900, 1280×800 và 960×600 đều đạt; kiểm tra chat đủ
  ba vai trò màu và thông báo lượt hết hạn. Đã xem trực tiếp ảnh Room Setup,
  placement Ready, Battle/chat và START.
- Log: `build/room-hud-chat-verification.log`, `build/room-hud-chat-final.log`.
  Ảnh: `build/font-review/<resolution>/room-settings-basic.png`, `placement.png`,
  `battle.png`, `countdown-start.png`.

Đây là kiểm tra tự động với render/input native và fixture. Chưa chơi thủ công
hai client kết nối PostgreSQL thật hoặc thử Cmd+V trên macOS trong lượt này.
Không tuyên bố đã kiểm chứng những trường hợp đó. Không tự commit thay đổi mới.

Đóng client cũ rồi chạy `./gradlew.bat :frontend:run`. Không cần migration hay
thay đổi phiên bản protocol/backend cho các sửa UI trong lượt này.
