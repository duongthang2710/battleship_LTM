# Phát hành Battleship trên EC2

Người chơi mở `Battleship.exe`, client tự kết nối TLS tới tên miền ở cổng `2209`, rồi hiện form đăng nhập. HAProxy nhận TLS trên EC2 và chuyển tiếp tới backend tại `127.0.0.1:2210`. PostgreSQL vẫn dùng cổng loopback `5432`.

## 1. DNS và Security Group

- Trong trang quản lý DNS, thêm bản ghi `A` cho subdomain game trỏ tới **Public IPv4** của EC2. Với Cloudflare, chọn **DNS only** (mây xám) để client kết nối TCP trực tiếp. Giữ các bản ghi của ứng dụng khác.
- Trong Security Group, giữ SSH `22` từ IP quản trị. Thêm **TCP `80` từ `0.0.0.0/0`** cho Certbot xác minh và gia hạn. Kiểm tra firewall trên Ubuntu nếu đang bật. Dành `2209` cho bước kiểm tra cuối; `2210` và `5432` không có rule inbound công khai.
- Kiểm tra `Resolve-DnsName game.example.com -Type A` trên Windows. Thay `game.example.com` bằng subdomain thật trong các lệnh bên dưới.

## 2. Build và upload

Chạy trong PowerShell tại thư mục project. `$key` là đường dẫn SSH key và `$ec2Ip` là Public IPv4 như khi SSH trước đó.

```powershell
.\gradlew.bat :backend:installDist :frontend:installDist :frontend:createGameRuntime
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
ssh -i $key "ubuntu@$ec2Ip" 'mkdir -p ~/battleship-release'
scp -i $key -r .\backend\build\install\backend .\deploy\ec2 "ubuntu@${ec2Ip}:battleship-release/"
ssh -i $key "ubuntu@$ec2Ip"
```

## 3. Cấp chứng chỉ và cấu hình EC2

Các lệnh sau chạy trên Ubuntu. Các bước này dùng thư mục `~/battleship` với `.env` và PostgreSQL Compose đã có. Thay tên miền và email trước khi chạy.

```bash
game_host='game.example.com'
acme_email='email-cua-ban@example.com'

sudo apt update
sudo apt install -y haproxy snapd
sudo snap install --classic certbot
sudo install -D -m 0755 ~/battleship-release/ec2/renew-battleship-tls.sh \
  /etc/letsencrypt/renewal-hooks/deploy/battleship-tls

sudo /snap/bin/certbot certonly --standalone --cert-name battleship \
  --domain "$game_host" --email "$acme_email" --agree-tos --non-interactive
```

Chỉ tiếp tục khi Certbot đã báo cấp chứng chỉ. HTTP `80` cần tiếp tục truy cập được để Certbot gia hạn tự động. Cấu hình dưới đây dành cho VM Battleship, nơi HAProxy chưa phục vụ ứng dụng khác.

```bash
sudo env RENEWED_LINEAGE=/etc/letsencrypt/live/battleship \
  /etc/letsencrypt/renewal-hooks/deploy/battleship-tls
sudo haproxy -c -f "$HOME/battleship-release/ec2/haproxy.cfg"

cd ~/battleship
sudo docker compose up -d --wait
sudo systemctl stop battleship.service 2>/dev/null || true
backup=$(mktemp -d "$HOME/battleship/backup.XXXXXX")
if [ -d backend ]; then mv backend "$backup/"; fi
mv ~/battleship-release/backend backend

sudo install -m 0644 ~/battleship-release/ec2/battleship.service /etc/systemd/system/battleship.service
sudo systemctl daemon-reload
sudo systemctl enable --now battleship.service
sudo install -m 0644 ~/battleship-release/ec2/haproxy.cfg /etc/haproxy/haproxy.cfg
sudo systemctl enable haproxy
sudo systemctl restart haproxy
```

Kiểm tra trên EC2. `ss` phải hiện Java lắng nghe ở `127.0.0.1:2210` (có thể hiện `[::ffff:127.0.0.1]:2210`) và HAProxy ở `0.0.0.0:2209`. OpenSSL phải báo xác minh chứng chỉ thành công. Kiểm tra thêm gia hạn thử trước khi phát hành.

```bash
sudo journalctl -u battleship -n 30 --no-pager
sudo ss -ltnp '( sport = :2209 or sport = :2210 )'
openssl s_client -connect 127.0.0.1:2209 -servername "$game_host" \
  -verify_hostname "$game_host" -verify_return_error -brief </dev/null
sudo /snap/bin/certbot renew --dry-run
```

## 4. Mở cổng game và phát bản Windows

Sau khi kiểm tra TLS, thêm **Custom TCP `2209` từ `0.0.0.0/0`** vào Security Group. Kiểm tra từ Windows bằng client bật TLS hoặc mở bản đóng gói, đăng nhập và kiểm tra hai tài khoản có thể vào cùng một phòng.

```powershell
.\gradlew.bat :frontend:run -PserverHost=game.example.com -PserverPort=2209 -PserverTls=true

powershell -NoProfile -ExecutionPolicy Bypass -File .\frontend\scripts\package-public-windows.ps1 -ServerHost game.example.com
```

File để chia sẻ là `frontend/build/public-release/Battleship-Windows-x64.zip`. Người chơi giải nén toàn bộ rồi mở `Battleship.exe`. Gói này đã kèm Java và kiểm tra chứng chỉ TLS trước khi gửi lệnh game. Nếu Public IPv4 của EC2 đổi, cập nhật bản ghi `A` của subdomain.

Tham khảo [Cloudflare DNS proxy](https://developers.cloudflare.com/dns/proxy-status/limitations/), [Certbot](https://eff-certbot.readthedocs.io/en/stable/using.html#standalone), [HAProxy TLS](https://www.haproxy.com/documentation/haproxy-configuration-tutorials/security/ssl-tls/basics-enable-tls/).
