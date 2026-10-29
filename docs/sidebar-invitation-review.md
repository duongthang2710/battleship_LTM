# Review cột HUD và lời mời PvP — 29/10/2026

## Thay đổi

- `frontend/.../ui/BattleSidebarLayout.java`: trục giữa chung lấy từ chiều rộng Chat.
  Banner/avatar, minimap và Chat được căn theo cùng tâm; không căn bằng ba offset
  riêng. Giữ cách Chat mở xuống dưới và khoảng cách dọc hiện có.
- `frontend/.../game/BattleHud.java`: nền dải tàu địch hẹp từ 84 xuống 72 logical px,
  opacity trắng từ 14% xuống 9%, viền ngoài tăng từ 1 lên 1,8 px. Nền kéo xuống
  phía sau phần trên avatar đối thủ; layer nền thấp hơn layer chân dung để không
  phủ màu trắng lên mặt. Mỗi ô tàu có viền trắng mảnh; dấu tàu chìm vẫn giữ.
- Avatar trái trên luôn là người đang đăng nhập; avatar phải dưới là đối thủ,
  hình chọn theo phe của từng người. Dải tàu phải lấy trạng thái từ enemy Board,
  không đổi thành dữ liệu tàu mình và không tiết lộ vị trí tàu chưa chìm.
- `frontend/.../game/OwnBoardMinimap.java`, `game/PlacementHud.java`,
  `chat/ChatPanel.java`: dùng chung các mốc bố cục mới; vùng chặn chuột đi cùng
  vùng hiển thị. Không đổi model, mapping grid, camera hay input gameplay.
- `frontend/.../pvp/GameInvitePanel.java`: thu nhỏ card 620×200 xuống 432×176;
  nút từ 176×38 xuống 138×32. Giữ font dễ đọc, tách title/timer, giới hạn tên theo
  chiều rộng thực tế; nội dung wrap bằng kích thước chữ thành tối đa hai dòng.
- `frontend/.../pvp/GameInviteController.java`: khi nhận ACCEPTED hoặc DECLINED,
  đóng hộp của lời mời đó, bỏ khỏi hàng chờ và không render lại trạng thái cuối
  thành hộp bắt buộc bấm Close. Giữ kết quả trong controller để các listener/test
  hiện có đọc trạng thái; lời mời khác trong hàng chờ vẫn được xử lý. Gửi lần mới
  có phản hồi đang gửi bình thường, không mở lại hộp của lần trước.
- Accepted vẫn gọi luồng mở Waiting Room hiện có. Pending vẫn có đếm ngược và
  thao tác hiện có; expired/invalidated/error vẫn giữ lý do với nút Close.

Không sửa backend, protocol, database, âm thanh, background, castle, ocean, board,
ships hoặc camera trong lượt này. Không tạo lại avatar.

## Kiểm tra thực tế

- Lệnh đầu: `build :frontend:checkCombatPresentation :frontend:checkTypographyRendering`.
  Các task build của frontend/backend/shared đã qua, gồm kiểm tra mới
  `checkInviteNotifications`. Phần render dừng vì assertion mới gọi `equals` trên
  tên node có thể null. Đã sửa null guard trong test; không phải lỗi runtime UI.
- `build/sidebar-invite-final-render.log`: **BUILD SUCCESSFUL** sau sửa assertion.
  OpenGL kiểm tra nền địch nằm sau chân dung, nối với avatar, đủ 17 viền cho đội
  hình năm tàu, role own/enemy đúng; các kiểm tra hiệu ứng/ẩn tàu vẫn qua.
- TypographyRenderChecks render **26 trạng thái × 3 kích thước**: 1440×900,
  1280×800, 960×600. Thêm preview lời mời đã gửi và lỗi. Đã xem ảnh Battle, incoming
  và outgoing; assertion so sánh tâm của ba geometry đã render xác nhận avatar,
  minimap và Chat thẳng hàng, không chỉ kiểm tra hằng số bố cục.
- `InviteNotificationChecks` mới dùng **TCP thật với server test mô phỏng phản hồi**:
  đủ bốn tổ hợp sender/recipient × ACCEPTED/DECLINED; hộp đóng, cập nhật tiếp không
  mở lại; gửi lời mời lần hai vẫn hoạt động. Hit target của hộp nhỏ được kiểm tra
  ở 960×600, 1280×800 và 1920×1080. Không dùng PostgreSQL trong bài này.
- `PvpRoomRenderChecks` đã cập nhật: khi kết quả là Accepted/Declined phải xác nhận
  hộp tự ẩn, thay vì bấm Close như behavior cũ. **Chưa chạy lại toàn bộ fixture hai
  cửa sổ 3D đó trong lượt này**; không gọi phần cập nhật test là đã kiểm thử xong.

Log đầu nằm tại `build/sidebar-invite-verification.log`; ảnh tại
`build/font-review/1280x800/battle.png`, `build/font-review/960x600/invite.png` và
`invite-sent.png`. Chưa chạy hai máy, macOS hoặc server PostgreSQL thật cho luồng
lời mời trong lượt này; không khẳng định các kiểm tra mô phỏng thay thế chúng.

Chỉ cần đóng client cũ rồi chạy lại `./gradlew.bat :frontend:run`. Không có migration
hay thay đổi protocol yêu cầu nâng cấp backend trong lượt này.
