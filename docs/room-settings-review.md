# Review cấu hình phòng PvP — 28/10/2026

## Các commit đã tạo trước khi sửa

| Commit | Phần Fleet Designer đã hoàn thành trước lượt này |
| --- | --- |
| `d24c867` | Model, hình tàu liên thông và luật chế độ Designer trong shared |
| `60ac71a` | Phòng và xác thực đội hình Designer trên server |
| `98f559e` | Editor vẽ tàu và model procedural trên client |
| `5c0c586` | Kiểm thử catalog hình, render và trận TCP |
| `0f8f016` | Tài liệu chế độ Designer |

Tại thời điểm review ban đầu, phần cấu hình phòng chưa commit. Đã chia commit ngày 29/10/2026: `ccb38c9`, `40db70e`; chưa push.

## Thay đổi và trách nhiệm

- `shared.game.GameRules`, `TurnRule`: cấu hình bất biến gồm chế độ, số tàu và luật lượt bắn.
  Ba tàu đầu là Carrier/Battleship/Cruiser; bốn tàu thêm Submarine; năm tàu thêm Destroyer.
- `Fleet`, `GameState`, `GameEngine`: kiểm tra Ready theo đúng bộ tàu của phòng; chặn loại không được bật;
  random và xác thực đội hình theo cùng cấu hình. Thắng khi đánh chìm toàn bộ đội hình đã cấu hình.
  Alternate đổi lượt sau mọi phát hợp lệ. Hit Continues giữ lượt khi trúng/đánh chìm, đổi lượt khi trượt.
- `CreateRoomRequest`, `RoomSnapshot`: truyền cấu hình qua protocol TCP hiện có; yêu cầu cũ thiếu các trường
  mới vẫn mặc định Classic/5 tàu/Alternate. Không thêm một hệ thống mạng khác.
- `backend.room.Room`, `RoomManager`: kiểm tra enum, số nguyên và giới hạn 3–5 trước khi tạo phòng;
  phòng sở hữu cấu hình. Join, Ready và rematch sử dụng lại cấu hình đó.
- `backend.match.MatchService`: xác thực cuối cùng và snapshot riêng từng người theo số tàu của phòng.
  Mỗi phát hợp lệ reset cửa sổ 60 giây, kể cả giữ lượt. Phát sai/trùng không reset.
  Luồng khóa trận/kiểm tra deadline vẫn dùng cơ chế hiện có.
- `frontend.pvp.PvpRoomPanel`, `PvpController`: PvP chỉ có Create/Join và nút quay lại.
  Create mở Room Settings; chỉ xác nhận Create mới gửi yêu cầu. Back/Esc về PvP mà không tạo phòng.
  Waiting Room hiển thị chế độ, số tàu và luật lượt.
- `ScreenController`, `GameController`, `PlacementHud`: nhận cấu hình server khi bắt đầu placement;
  chỉ hiện loại tàu được dùng, cập nhật tổng số và Ready. Phím chọn loại bị tắt không xóa bản nháp.
- README và tài liệu tham chiếu đã cập nhật luồng và luật mới. Không cần migration database cho cấu hình phiên phòng.

## Kiểm thử đã chạy

| Kiểm tra | Kết quả thực tế |
| --- | --- |
| Gradle `build` | Đạt; các kiểm tra Classic/Designer, tài khoản, timer, điều hướng và shared hiện có chạy thành công |
| `checkRoomRules` | Đạt 12 tổ hợp chế độ × số tàu × luật lượt; thiếu/sai loại, Ready, privacy, thắng, timer với clock kiểm soát và sát deadline |
| `checkRoomSettingsTcp -PdatabaseChecks` | Đạt với TcpServer thật, PostgreSQL và hai socket đã đăng nhập; cấu hình sai bị từ chối, create/join/Ready, 12 trận đầy đủ và 2 ván rematch |
| `checkJoinConnection` | Đạt; adapter client qua server kịch bản xác nhận configure/Back không tạo phòng, xác nhận gửi đúng Designer/3 tàu/Hit Continues một lần; Join/reconnect cũ vẫn đạt |
| `checkTypographyRendering --args="1280 800"` | Đạt 20 màn render OpenGL; đã xem ảnh Room Settings Basic/5 tàu và Designer/3 tàu |
| `checkDesignerRendering --args=placement` | Đạt thao tác vẽ/xóa/kéo/xoay 5 tàu, sau đó vẽ 3 tàu và HUD/Ready đúng; phím tàu bị tắt giữ nguyên bản nháp |
| `git diff --check` | Đạt |

Log: `build/room-settings-verification.log`, `build/room-settings-client-check.log`,
`build/room-settings-placement-check.log`. Ảnh menu nằm trong `build/font-review/1280x800/room-settings-*.png`.

Các kiểm tra TCP là hai kết nối TCP thật, không phải hai ứng dụng 3D cùng chơi một trận.
Render/input được kiểm tra riêng bằng OpenGL. Chưa kiểm tra thủ công hai client 3D, hai máy LAN hoặc macOS
trong lượt này. Kiểm tra timeout mới dùng clock kiểm soát, không chờ đủ 60 giây thực tế.
Log có cảnh báo presence `08001` lúc đóng server kiểm thử; chưa kiểm tra lại hệ thống bạn bè trong lượt này.

Không sửa source/asset background, castle, ocean, board visual, ship builder, camera hoặc ánh sáng.
Khi dùng bản mới, khởi động lại backend và cả hai client từ cùng phiên bản source.
