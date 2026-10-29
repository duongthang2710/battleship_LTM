# Review chia commit — 30/10/2026

Chia các thay đổi đã có từ `ebae66a` theo chức năng. Không thêm tính năng hoặc sửa
source trong lượt này; chỉ tổ chức index và tạo commit, không push.

## Các nhóm commit

| Commit | Phạm vi |
| --- | --- |
| `907f61a` | Khoảng cách chữ trên tài khoản, bạn bè, leaderboard, màn kết quả; icon copy mã phòng nhỏ hơn và bố cục phòng chờ |
| `f0ed722` | Tàu Designer, xác tàu chìm, nguồn bắn từ tàu biển, projectile/HIT/MISS, hướng nhãn bàn cờ |
| `d1e1956` | Bộ âm thanh có giấy phép và credits, nhạc/môi trường/hiệu ứng, xử lý đuôi âm, volume/mute và lưu Settings, importer và checks audio |
| `c941e98` | Countdown chuẩn bị Battle ở server/shared, chặn bắn sớm, bảo toàn đủ 60 giây lượt đầu và các kiểm tra backend/TCP tương ứng |
| `16af8e3` | HUD portrait/cung timer, placement gọn hơn, sidebar/avatar/minimap/chat chung trục; kết nối countdown/audio với controller và Settings trong trận |
| `569bcac` | Hộp lời mời gọn, tự đóng sau Accept/Decline ở cả hai phía, gửi lời mời kế tiếp; kiểm tra TCP bằng server mô phỏng và cập nhật fixture UI |

Commit tài liệu tiếp theo chứa README và các review chi tiết, bao gồm tài liệu này.
Các phần dùng chung trong `frontend/build.gradle` và `GameInviteController` được
stage riêng theo trách nhiệm; không ghi đè file làm việc để chia commit.

## Trách nhiệm và phụ thuộc

- Backend/shared giữ thời gian chính thức. Frontend chỉ vẽ countdown và chặn
  thao tác trong thời gian chuẩn bị; không tự chốt thắng/thua.
- Renderer dùng kết quả gameplay có sẵn; chỉ thay cách trình diễn đạn, impact và
  tàu hỏng. Không thêm luật mới.
- Audio nền tảng được commit trước các hook gọi từ UI/game. Credits gốc được giữ
  nguyên, kể cả khoảng trắng của văn bản giấy phép bên thứ ba.
- HUD và sửa lời mời tách thành hai commit; phần onSound của lời mời thuộc nhóm
  HUD/tích hợp, còn vòng đời thông báo và kích thước thuộc nhóm invitations.
- Không đưa `.idea/gradle.xml`, `.idea/modules.xml`, `.idea/vcs.xml` vào commit.

## Kiểm tra trong lượt chia commit

- Kiểm tra diff đã stage của từng nhóm. Kiểm tra whitespace code/tài liệu; loại
  trừ văn bản giấy phép gốc khỏi yêu cầu chuẩn hóa whitespace.
- Ghi SHA-256 của toàn bộ file thay đổi trước khi stage và đối chiếu sau khi
  commit để bảo đảm nội dung file làm việc được giữ nguyên.
- Kiểm tra cuối chỉ còn các thay đổi IDE chưa commit; index không còn staging.
- Không chạy lại build/game: lượt này chỉ chia commit, không thay source. Kết quả
  kiểm thử cũ vẫn được trình bày với phạm vi thật trong các review tương ứng.

## Các review kỹ thuật đã có

- `naval-visual-review.md`: model/VFX/nhãn, OpenGL và các giới hạn kiểm thử.
- `placement-battle-ui-review.md`, `reference-hud-audio-review.md`: nhịp phát bắn,
  HUD và lịch sử triển khai bộ âm đầu tiên; đây là review theo thời điểm.
- `downloaded-audio-review.md`: bộ âm tải có giấy phép thay bộ procedural ban đầu.
- `hud-layout-countdown-review.md`: countdown server và bố cục HUD.
- `sidebar-invitation-review.md`: căn sidebar và thông báo lời mời.
- `avatar-audio-layer-review.md`: thứ tự lớp avatar và dừng đuôi âm chiến đấu;
  native OpenGL/Java Sound đạt, không thay thế nghe A/B hoặc hai client PvP.

Backend và cả hai client phải chạy cùng bản protocol có `startsInMillis`; dừng
phiên bản cũ rồi khởi động lại sau khi cập nhật code trên các máy demo.
