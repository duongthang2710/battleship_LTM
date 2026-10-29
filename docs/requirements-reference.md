# Định hướng theo báo cáo Battleship

Đối chiếu ngày 25/10/2026. Nguồn: `D:\Tài liệu\Kì 1 N4\LTM\B23DCCN752_Duong_Trong_Thang.docx`.
SHA-256: `A8363B553F85E694AA58EF9876B61EB4FC7FFE8D0737078D50B0060F79AF607E`.
Đã đọc phần thiết kế chung và xem sơ đồ database. Phần cá nhân không áp dụng theo yêu cầu mới nhất.

Tài liệu định hướng phát triển; danh sách dưới đây không phải yêu cầu triển khai tất cả ngay.
Tôn trọng phạm vi từng lượt và các quyết định trực tiếp của người dùng. Không tự thay luật đang chạy khi báo cáo khác yêu cầu đã chốt.

## Phạm vi chính

- Phần chung: đúng hai chế độ PvP và PvAI; không có Local Game hai người trên cùng máy.
- PvP: hai client qua cùng Java TCP server; server quản lý phòng, đội hình bí mật, lượt, thời gian và kết quả. Client không tự quyết định hit/miss.
- PvAI: điều khiển trận và bot trong ứng dụng, không cần phòng/ghép người. Chưa triển khai trong project hiện tại.
- Giữ `frontend` / `backend` / `shared`; tách socket, nghiệp vụ, UI và renderer. Tái sử dụng luật dùng chung, không tạo class trùng chỉ để giống tên trong sơ đồ.
- Quyết định hiện hành bổ sung: Java 25, jMonkeyEngine, PostgreSQL bằng Docker Compose; đăng nhập bắt buộc trước Main Menu; đã bỏ Play Local. Giữ nguyên visual đã chốt.

## Phạm vi áp dụng đã được người dùng chốt

Chỉ đối chiếu **Phần 1 — thiết kế chung**. Bỏ qua Phần 2 cá nhân; không ưu tiên riêng session/heartbeat và không áp sơ đồ cá nhân vào project.
Giữ chức năng hiện có, bổ sung thiếu sót trong phần đã triển khai. Không tự mở thêm PvAI, chat, bạn bè, ghép trận hoặc đầu hàng.
Người dùng xác nhận giữ **5 loại tàu thẳng**, không chuyển sang nhóm ô D4. Giữ quyết định 60 giây hết lượt thì thua.

## Đối chiếu với code hiện tại

| Nội dung | Hiện trạng và phần cần đối chiếu khi được giao |
| --- | --- |
| Đăng nhập và framing | Có xác thực backend và TCP length-prefixed JSON. Giữ protocol đang hoạt động; không áp tên message từ Phần 2. |
| Heartbeat | Có `PING/PONG` mỗi 10 giây và read timeout client/server, đáp ứng nền tảng phát hiện gián đoạn của phần chung. Không triển khai sequence/registry của phần cá nhân trong lượt này. |
| Mất kết nối trong trận | Đã bổ sung: người còn lại thắng, server chốt `GAME_OVER/PLAYER_LEFT`, không lộ tàu, khóa bắn/timer, hủy rematch với người đã rời. Waiting/Placement vẫn cập nhật phòng như cũ. |
| Hết thời gian lượt | Mục 9.1 yêu cầu tự bắn ngẫu nhiên vào ô chưa bắn rồi đổi lượt. Yêu cầu trực tiếp trước đây và code hiện tại: 60 giây không bắn hợp lệ thì thua. Đây là khác biệt cần chốt khi sửa timer, chưa tự đổi. |
| Hạm đội | Tài liệu dùng 17 ô, 5 nhóm liên thông D4. Code dùng 5 tàu thẳng kích thước 5/4/3/3/2, kiểm tra biên/chồng lấn; chưa đếm thành phần liên thông D4 giữa các tàu. Người dùng đã chọn giữ nguyên 5 loại tàu thẳng; không áp quy tắc D4 giữa các tàu. |
| Phòng và chế độ | Có tạo/join bằng mã và Ready. Tài liệu còn ghép ngẫu nhiên/phòng công khai, PvAI và đầu hàng; chưa triển khai. |
| Kết nối và kết quả UI | Đã bổ sung nhập IP/hostname/cổng trên form tài khoản, kiểm tra cổng, giữ trạng thái kết nối. Màn kết quả hiện lý do thắng/thua, giữ bố cục hiện tại. |
| Database | Có `users`, `matches`, `match_players`; bổ sung `last_seen_at`, `mode`, `winner_slot`, `total_turn`, `player_type` bằng migration giữ dữ liệu. Chưa làm avatar/quản lý trạng thái tài khoản/AI level/conversations/messages vì thuộc chức năng chưa triển khai. Giữ PK UUID. |

Các lượt tiếp theo đối chiếu thiết kế chung và phạm vi được giao; kiểm tra code/protocol trước khi sửa. Những chức năng chưa triển khai chỉ bổ sung khi được yêu cầu.

Bạn bè đã được bổ sung theo yêu cầu riêng: lời mời, quan hệ một cặp duy nhất, online/offline qua phiên TCP và migration V003. Đã tích hợp mời bạn online vào phòng PvP qua TCP, lời mời trong RAM hết hạn sau 60 giây; đã có chat trong phòng chờ và trận PvP theo yêu cầu riêng. Chat riêng giữa bạn bè chưa triển khai.


## Bổ sung theo yêu cầu mới: Fleet designer

Yêu cầu ngày 28/10/2026 cho phép thêm **chế độ riêng** tự vẽ tàu bằng ô nối cạnh D4.
Giữ 5 loại/kích thước 5/4/3/3/2; không chồng lấn hoặc vượt biên. Chế độ Classic và thao tác cũ vẫn giữ tàu thẳng.
Server chọn bộ luật từ phòng, xác thực đội hình và giữ luật 60 giây hết lượt thì thua. Đây là mở rộng được
người dùng yêu cầu trực tiếp, thay cho giới hạn chỉ dùng tàu thẳng đối với chế độ mới.


## Cấu hình phòng theo yêu cầu mới

Người tạo phòng chọn Basic hoặc Fleet Designer, 3–5 tàu và luật bắn luân phiên
hoặc trúng/đánh chìm được bắn tiếp. Thứ tự loại tàu là Carrier, Battleship, Cruiser,
Submarine, Destroyer; số lượng chọn quyết định lấy 3, 4 hoặc cả 5 loại đầu tiên.
Đây là thay đổi trực tiếp từ người dùng cho cả hai chế độ. Giữ timeout 60 giây;
mỗi phát bắn hợp lệ reset thời gian, phát sai hoặc trùng không reset. Server sở hữu
cấu hình phòng, giữ cấu hình khi chơi lại và không nhận luật từ lệnh bắn của client.
