# Review Room Options và sửa UI — 31/10/2026

## Room Setup

- Cột trái bỏ preset và lời dẫn dài. Thay bằng danh sách đội tàu cập nhật theo
  3/4/5 tàu; Create Room và Back đặt dưới cùng. Cột phải còn nhãn và lựa chọn.
- Giữ Basic/Designer, số tàu, luật bắn. Thêm **Starting Fleet: Empty Board /
  Shuffled Fleet** và **Enemy Fleet Report: Show Losses / Hide Report**.
- Shuffled Fleet dùng random placement hiện có, không tự Ready; cả hai người
  vẫn được chỉnh tàu rồi gửi đội hình qua kiểm tra server. Hide Report ẩn dải
  trạng thái hạm đội đối thủ bên phải; thông báo hit/sunk và luật bắn giữ nguyên.
- `shared/room/RoomOptions.java` là dữ liệu chung cho hai tùy chọn. Bổ sung vào
  `CreateRoomRequest` và `RoomSnapshot`, giữ constructor/default cho ba thiết lập
  cũ. `backend/room/RoomManager` kiểm tra kiểu boolean, lưu ở `Room`, gửi qua
  snapshot và giữ nguyên khi rematch trong cùng phòng.
- `PvpRoomPanel`/`PvpController` chọn và gửi dữ liệu; `GameController` áp dụng
  đội hình ban đầu qua GameEngine và bật/tắt report trong BattleHud.

## Viền UI

- `ui/UiShapes`: thêm mesh ring có góc nối liền, offset viền vào trong polygon.
  Không ghép cạnh bằng nhiều quad xoay độc lập nên không có đầu nét thò ra tại
  các giao điểm. Viền chính khoảng 1.6–2 pixel, rõ hơn nét mảnh trước đó.
- Fill hình chữ nhật tương ứng cũng được cắt góc đúng theo viền; không còn phần
  nền vuông thừa ngoài góc vát. Không đổi hit target hoặc tọa độ các nút.
- Áp dụng cho Account, Main Menu, Room, Friends, Leaderboard, Settings, invitation,
  connection notice, Game Over, chat phòng chờ và nút placement/battle. Banner
  chỉ huy và nền trắng phía địch cũng dùng contour viền liền.
- `NavalHudSkin` giữ palette hiện tại; không thay ảnh nền hoặc asset.

## Các sửa HUD khác

- `OwnBoardMinimap`: các ô cùng tàu custom nối bằng cầu hull/deck cùng material,
  tạo silhouette liên tục. Tàu khác vẫn tách; không nối ô giữa hai ship ID.
  Bốn gem xoay quanh đúng tâm góc, kiểm tra vị trí trong world-space UI sau xoay.
- `ChatPanel`: bỏ nút thu nhỏ trong Battle; log/input/scrollbar và Enter giữ nguyên.
- `BattleHud`: avatar địch tăng từ radius 62 lên 74, gần cân bằng avatar mình 80;
  nâng nhẹ để không đè lên nameplate. Countdown rộng 360 thay cho 540 (hai phần ba).
- `PlacementHud` và Waiting Room: nút Leave có nền đỏ.
- `LeaveConfirmationPanel` + `input/LeaveConfirmationInput`: modal giữa màn hình,
  Leave / Cancel; Esc hủy. Chặn input bắn/đặt/xoay trong khi mở, không gửi yêu cầu
  rời phòng trước khi xác nhận. `ScreenController` nối cả nút Leave và Esc từ
  phòng/trận vào modal; GameController dùng callback thay vì gọi socket/UI modal.

## Bảo toàn

Giữ nguyên background, castle, sky, ocean, scene 3D, board, model tàu, ánh sáng,
camera, tài khoản/database và luật 60 giây. Không thêm gameplay AI/chat/network
khác. Không sửa cấu hình IDE hoặc tự commit các thay đổi mới.

## Kiểm thử thực tế

- `gradlew build :frontend:checkCombatPresentation :frontend:checkTypographyRendering`
  đạt: compile cả ba module và các kiểm tra hiện có. TCP client adapter với
  scripted server kiểm tra CreateRoomRequest/RoomSnapshot mang hai tùy chọn mới.
- Kiểm tra cuối `:backend:checkRoomSettingsTcp -PdatabaseChecks
  :frontend:checkUiFeedback :frontend:checkCombatPresentation
  :frontend:checkTypographyRendering` đạt.
- Server Java + PostgreSQL thật và hai TCP peers: 12 cấu hình mode/số tàu/luật,
  thêm biến thể hai tùy chọn; creator/joiner nhận đúng snapshot; từ chối option
  sai kiểu; đặt tàu, đấu hết trận và hai rematch giữ nguyên cấu hình.
  Tài khoản/trận QA của fixture được fixture dọn sau khi chạy.
- UI/input checks: frame vertices nằm trong bounds; Cancel/Esc không gọi leave,
  Confirm gọi một lần, modal chặn phím gameplay. Đây là kiểm tra listener/callback,
  không phải thao tác chuột trên hai cửa sổ PvP thật.
- OpenGL thật: hull bridge cho tàu Designer; đủ bốn gem có tâm đúng bốn góc sau
  xoay UI; các kiểm tra projectile/timer/placement cũ vẫn đạt.
- Render 27 trạng thái ở 1440×900, 1280×800 và 960×600 đạt. Đã xem ảnh cuối của
  Room Setup, minimap custom, Battle/avatar, Leave dialog; sửa thêm khoảng cách
  tên tàu/counter ở cột trái và khoảng cách avatar địch với nameplate.
- Log: `build/room-options-frames-verification.log`, `build/room-options-final.log`.
  Ảnh: `build/font-review/<resolution>/room-settings-basic.png`,
  `room-settings-designer-3.png`, `battle.png`, `leave-confirmation.png`;
  minimap custom ở `build/combat-review/ocean-projectile.png`.

Chưa kiểm thử thủ công hai client 3D hoặc macOS trong lượt này. Hai TCP peers là
kiểm tra server/protocol thật, không thay thế thao tác trên hai cửa sổ game.

## Chạy lại

Dừng backend và hai client cũ, chạy lại `./gradlew.bat :backend:run` và
`./gradlew.bat :frontend:run`. Hai máy cần cùng bản cập nhật để cùng áp dụng các
RoomOptions mới. Không có migration hoặc thay đổi dữ liệu tài khoản.
