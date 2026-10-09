# TV Space — kế hoạch triển khai super app TV

Ngày nghiên cứu: 08/10/2026. Package giữ `com.tuntech.supertvstreamcast`; tên sản phẩm làm việc: **TV Space**.

## Mục tiêu và phạm vi
Một binary Android/iOS: TV Remote + screen sharing + IPTV. Chọn hãng TV khi onboarding; cùng hệ thống tính năng, khác hướng dẫn và adapter kết nối. Không tạo nhiều app trùng nhau theo hãng. Store listing và chiến dịch quảng cáo chia theo intent/hãng; mỗi nhóm chỉ quảng bá khả năng đã nghiệm thu trên thiết bị.

Đây là roadmap theo cổng nghiệm thu, không phải cam kết “mọi TV đều hỗ trợ”. Có thể hoàn thành UI/logic trước, nhưng tương thích firmware và kết nối thật phải được xác minh riêng. Không giả số rating, review, số kênh hoặc kết nối thành công.

## Tham khảo thị trường
Các app có quy mô đáng kể hoặc sản phẩm chuyên biệt để học hỏi; không khẳng định đây là bảng xếp hạng doanh thu theo thời gian thực. Chỉ khảo sát store listing, chưa kiểm thử trực tiếp từng app đối thủ. Số lượt tải là snapshot từ trang store truy cập ngày nghiên cứu.

| Tham khảo | Bằng chứng | Học gì / áp dụng |
|---|---|---|
| [Lean Remote](https://play.google.com/store/apps/details?id=co.leanremote.universalremotecontrol.remotecontrol) | Trang Play hiển thị 10M+ downloads; smart TV và IR | Brand-first setup; phân biệt Wi-Fi và IR; điều khiển nằm trong vùng ngón tay |
| [Web Video Cast](https://play.google.com/store/apps/details?id=com.instantbits.cast.webvideo) | Trang Play hiển thị 50M+ downloads; web/local video, photos, audio | Trạng thái receiver chung; giải thích cast nội dung so với chiếu toàn màn hình |
| [TiviMate](https://play.google.com/store/apps/details?id=ar.tvplayer.tv) | IPTV player dành cho Android TV | Playlist người dùng, phân nhóm, favorites, lịch chương trình trong giai đoạn mở rộng |
| [UHF](https://apps.apple.com/us/app/uhf-love-your-iptv/id6443751726) | Sản phẩm IPTV trên hệ sinh thái Apple | Trải nghiệm thư viện và player nhất quán giữa thiết bị |

Không sao chép brand asset hay giao diện đối thủ. Artwork và icon của TV Space được tạo riêng.

## Quy tắc kế thừa từ PetTranslator
Nguồn: `../PetTranslator/CLAUDE.md`.

- UI → domain → data; commonMain không gọi API Android/iOS trực tiếp.
- Screen lấy `StateFlow` từ ViewModel; composable con nhận state/callback.
- Logic có tests ở commonTest. KMP mới dùng `testAndroidHostTest` vì module hiện tại dùng plugin Android Multiplatform mới; không sao chép lệnh `:composeApp` từ dự án khác.
- Externalize strings; dùng design token; icon vector trước, art raster WebP; không bake chữ vào ảnh.
- Không sửa hoặc chép file từ các thư viện tuntech. Tích hợp cả module với package gốc khi đến giai đoạn monetization.
- Tài liệu chức năng đã triển khai ở `docs/context`; kế hoạch chưa hoàn thành ở `docs/plans`.
- Không sao chép app ID, Firebase, ad unit hay khóa của PetTranslator/ElectronicsRepair.
- Bản phát triển hiện có English/Vietnamese; bản phát hành quốc tế cần đủ 13 ngôn ngữ như template (en, vi, es, pt, fr, de, id/in, hi, th, ko, ja, tr, ar), review người bản ngữ và RTL.

## Flow tương tự ElectronicsRepair

ElectronicsRepair: Splash → onboarding theo lựa chọn người dùng → introduction ad → paywall → Home; lần sau Splash → Home → startup paywall nếu cấu hình cho phép; xin permission tại thời điểm sử dụng. Tham khảo trực tiếp `OnboardingViewModel.kt`, `AppFlowEffect.kt` và `CLAUDE.md` của dự án.

TV Space:

1. App shell/load preferences (không delay splash giả).
2. Trang giới thiệu artwork + giá trị sản phẩm.
3. Chọn hãng: Samsung, LG, Sony BRAVIA, Google/Android TV, TCL, Hisense, Roku, TV khác.
4. Chọn nhu cầu: Remote / Mirror / IPTV.
5. Khi hoàn tất, lưu hãng/nhu cầu trên thiết bị và mở tab đã chọn; lần sau vào Home.
6. Kết nối/permission tại điểm dùng. Remote chưa kết nối phải disabled. IPTV không yêu cầu kết nối TV để xem trên điện thoại.
7. Giai đoạn monetization: introduction ad → first-start paywall có nút đóng → Home; kiểm tra entitlement trước startup paywall; Pro không quảng cáo; không interstitial khi chuyển tab hoặc mỗi lần bấm remote.

Paywall chỉ liệt kê product thật từ Qonversion; khi chưa có project key thì không tự mở paywall.

## Thiết kế — Midnight Cinema

- Nền navy `#07111F`, surface `#122135`, raised `#1B2E45`.
- Cyan `#67E5EF` cho hành động; coral `#FFAE9B` cho điểm nhấn/favorites.
- Typography Material 3; bo góc 18–24 dp; vùng chạm tối thiểu 48 dp; CTA 56 dp.
- Hero TV + điện thoại + remote, không chữ; WebP trong Compose Resources.
- Icon launcher: TV cyan, play coral; có SVG gốc, adaptive Android và PNG iOS.
- Icon điều hướng vẽ vector bằng Compose Canvas, cùng nét và palette.
- Home ưu tiên trạng thái kết nối rồi 3 feature cards. Remote tối giản, IPTV lazy list + search/favorites, mirror hướng dẫn cụ thể theo nền tảng.

## Các giai đoạn và cổng nghiệm thu

### GĐ1 — Nền tảng và design (đã triển khai code)
- KMP giữ cấu trúc :androidApp + :shared + iosApp hiện có.
- Design token, hero, launcher, onboarding có persistence, tab shell, settings.
- ViewModel StateFlow, repository/platform seams; EN/VI externalized.
- Gate: Android APK build; iOS common/platform compile; test logic; kiểm tra màn hình nhỏ, font lớn và accessibility trên thiết bị.

### GĐ2 — Vertical slice dùng thật (đã triển khai code, cần nghiệm thu TV thật)
- Sony BRAVIA IP + PSK: chỉ địa chỉ RFC1918, discovery IRCC codes từ TV, gửi lệnh SOAP; timeout, lỗi xác thực/kết nối, disconnect.
- M3U nội dung/URL HTTP(S): đọc dạng stream, giới hạn 64 MB/50.000 kênh (cắt bớt, không báo lỗi), loại scheme không hỗ trợ, dedupe, group, search, favorites.
- Android 17 LAN permission declared/requested at point of use, localized denial and back handling. See [local network permission](https://developer.android.com/privacy-and-security/local-network-permission).
- Native player: Android Media3 ExoPlayer + PlayerView (HLS/TS, từ 09/10/2026); iOS AVPlayerViewController. Dừng/pause theo lifecycle.
- Screen sharing: mở Android Cast settings; iOS hướng dẫn Control Center/AirPlay. Đây là tích hợp luồng hệ thống, chưa phải streaming engine riêng.
- Gate: BRAVIA model/firmware thật, PSK đúng/sai, mất Wi-Fi, TV sleep; playlist thật HLS/live; Android Cast receiver/AirPlay thật; codec và lỗi playback.

### GĐ3 — Remote đa hãng (đã triển khai code cho Sony/Samsung/LG + quét LAN; cần nghiệm thu TV thật)
- ✅ Interface RemoteAdapter + `createAdapter`; Sony, Samsung Tizen, LG webOS adapters; capability từ phản hồi TV. Xem `docs/context/remote-discovery-iptv.md`.
- ✅ Quét /24 unicast theo thao tác người dùng (Samsung API, Sony interface info, LG SSAP). ✅ 09/10/2026: Wake-on-LAN (địa chỉ MAC do TV báo về) và lưu token ghép nối bằng Keystore/Keychain kèm nút "Quên các TV đã ghép nối". Còn lại: SSDP/Bonjour; entitlement multicast của Apple để gửi Wake-on-LAN trên iPhone thật.
- Samsung Tizen: ✅ pairing chấp thuận trên TV, token lưu mã hóa trên máy, WSS pin chứng chỉ theo host (TOFU), Wake-on-LAN qua `wifiMac`. Còn: test model 2016–2026.
- LG webOS: ✅ SSAP register (PROMPT, manifest không chữ ký), pointer socket, IME, launch points. Cần xác minh firmware 2025/2026 có chấp nhận manifest không ký; nếu không, cân nhắc module được duy trì.
- Android/Google TV: ✅ 09/10/2026 Remote v2 (chứng chỉ client trong Keystore/Keychain, ghép nối bằng mã 6 ký tự trên TV, pin chứng chỉ TV, phím bấm). Còn: nhập chữ (IME), mở app (app link), giữ phím, tìm TV qua mDNS `_androidtvremote2._tcp`, nghiệm thu trên TV thật. Cast không thay thế giao thức điều khiển TV.
- TCL/Hisense phân theo hệ điều hành thật (Google TV / Roku / VIDAA), không chọn giao thức chỉ theo nhãn hãng.
- Discovery: Android NSD/SSDP, iOS Bonjour/SSDP phù hợp entitlement; manual IP fallback; chỉ scan LAN sau thao tác người dùng.
- Roku: [tài liệu ECP](https://developer.roku.com/dev/docs/external-control-api) hiện ghi không gửi ECP từ third-party mobile platforms. Chưa kích hoạt remote Roku; xác minh khả năng tích hợp được phép trước release.
- Gate: matrix ≥2 model/firmware cho mỗi adapter, permission denied, reconnect, token revoked, TV asleep; trạng thái capability xác thực trước enable nút.

### GĐ4 — Casting/mirroring nâng cao + IPTV library (chưa triển khai)
- Google Cast SDK cho media URL; DLNA receiver tương thích; AirPlay route picker iOS.
- Mirroring Android thực sự: MediaProjection consent mỗi session → foreground service → encoder → transport → receiver companion. Không dùng Default Media Receiver như một encoder/mirroring engine có sẵn.
- iOS: ReplayKit broadcast extension + receiver riêng nếu cần streaming trong app; AirPlay hệ thống vẫn là đường ưu tiên.
- Theo [Android MediaProjection](https://developer.android.com/media/grow/media-projection), áp dụng permission/service và session lifecycle phù hợp SDK mục tiêu.
- Persist playlist/favorites vào storage phù hợp; hiện library chỉ tồn tại trong session để tránh lưu token stream vào prefs không bảo vệ.
- ✅ 09/10/2026 (code, chưa chạy trên thiết bị): thư viện IPTV lưu trên máy với nhiều nguồn, Xtream phim/series, xem tiếp, yêu thích, kênh ẩn + mật mã, lịch chương trình theo kênh, liên kết chia sẻ `tvspace://`. Xem `docs/context/iptv-library.md`.
- ✅ Nguồn IPTV: URL playlist (HTTP/HTTPS), Xtream Codes (live), tệp trên thiết bị, phát một luồng, EPG XMLTV now/next + hướng dẫn sử dụng. Xem `docs/context/iptv-sources.md`.
- ✅ 09/10/2026: parser M3U chịu lỗi (logo, user-agent/referer), tải dạng stream + theo redirect, hủy/tiến độ nhập, EPG `.gz`, player Media3, logo kênh.
- Còn lại IPTV (so với `../iptv_kmp`): mã hóa tệp thư viện bằng khóa Keystore/Keychain (hiện dựa vào bộ nhớ riêng của app, không sao lưu); Xtream catch-up; tab EPG riêng với lưới theo giờ, chọn ngày và nhắc lịch (thông báo); điều khiển player riêng (audio/phụ đề/tỉ lệ/tốc độ), mini player, PiP, nút Cast/AirPlay; iOS phát `.ts` thô (cần engine ngoài AVPlayer); QR chia sẻ/quét; đồng bộ nguồn sang TV; giới hạn bản miễn phí (số nguồn, thời lượng xem) khi có Qonversion thật; test `IptvViewModel`. Chi tiết ở cuối `docs/context/iptv-library.md`.
- Media3 Android, mở rộng AVPlayer iOS, multiple playlists, playback controls, subtitles/track selection.
- Gate IPTV nguồn mới: tài khoản Xtream thật (đúng/sai/hết hạn, panel HTTP và HTTPS), XMLTV thật cỡ lớn (thời gian parse/bộ nhớ trên máy yếu), document picker Android/iOS (Drive/iCloud), luồng HLS/TS thật.
- Gate: receiver thật, 30 phút live, screen off/revoke/rotation, latency/fps/memory đo được; không chiếu DRM protected content; link có token không lọt analytics.

### GĐ5 — Monetization, ASO và release (flow vào app đã triển khai code; chưa có ID thật)
- ✅ Submodules tuntech (`common`, `monetization`, `mmp`, `mmp_firebase`) và flow Splash → Onboarding → Paywall → Dashboard theo cấu trúc Smart Printer / Cam Scanner. Xem `docs/context/entry-flow.md`.
- Còn lại: tạo Firebase project, Qonversion project + products, AdMob app/ad units riêng và thay các giá trị giữ chỗ; đẩy Remote Config lên console; URL điều khoản/quyền riêng tư; nghiệm thu UMP consent, mua/khôi phục, app-open ad trên thiết bị thật. (09/10/2026: iOS đã build bằng Xcode và mở được trên simulator.)
- Chưa thêm: `mmp_facebook`, `liquid_glass`, `ai`, màn Language, push notification, App Check, build type staging, R8/release signing.
- Free: kết nối và remote cơ bản. Pro: trải nghiệm không ads, library nâng cao, casting nâng cao sau khi hỗ trợ thật. Giới hạn cụ thể phải được xác định bằng test giá và entitlement thật.
- Analytics: onboarding → connect_attempt → connect_success → feature_used → paywall → purchase. Không log IP, PSK, playlist URL/token.
- 13 ngôn ngữ, RTL, VoiceOver/TalkBack, dynamic type, privacy/data safety, license audit, release signing, TestFlight/internal testing.
- Store: một app, custom listings/product pages/ads theo hãng; chỉ mở group từ khóa tương ứng adapter đã qua gate.

## Ma trận hiện tại

| Khả năng | Android | iOS | Giới hạn hiện tại |
|---|---|---|---|
| Onboarding, Home, chọn hãng | Implemented | Implemented | EN/VI |
| Flow Splash/Paywall/ads (tuntech modules) | Implemented | Implemented (build Xcode + mở trên simulator) | Firebase/Qonversion/AdMob đang là giá trị giữ chỗ; chưa test mua hàng/quảng cáo thật |
| Sony BRAVIA Remote | Implemented | Implemented | Manual IP + PSK; model hỗ trợ IP control; chưa test TV thật |
| Samsung Tizen / LG webOS remote | Implemented | Implemented | Pairing trên TV; token lưu mã hóa (Keystore/Keychain); chưa test TV thật |
| Google TV / Android TV remote | Implemented | Implemented | Nhập IP thủ công + mã trên TV; chỉ phím bấm (chưa nhập chữ/app); chưa test TV thật |
| Wake-on-LAN (Samsung/LG/Sony) | Implemented | Implemented (thiết bị thật cần entitlement multicast) | Chỉ khi TV đã báo địa chỉ MAC ở lần kết nối trước; không xác nhận được TV đã bật |
| Roku, VIDAA remote | Roadmap | Roadmap | Chọn hãng chỉ cá nhân hóa, không kết nối giả |
| Touchpad, bàn phím số, nhập chữ, app TV | Implemented | Implemented | Theo capability từng adapter |
| Screen sharing hệ thống | Cast settings | AirPlay hướng dẫn | Receiver/system quyết định hỗ trợ; không báo mirroring active |
| IPTV M3U import và native player | Implemented | Implemented | Nhiều nguồn lưu trên máy, nhóm, xem tiếp, yêu thích, kênh ẩn; chưa chạy thử trên thiết bị sau khi đổi sang thư viện lưu trữ |
| IPTV Xtream Codes, tệp thiết bị, luồng đơn, EPG XMLTV | Implemented | Implemented | Xtream live + phim + series; EPG `.xml`/`.xml.gz` (không lưu, tải lại mỗi phiên); iOS không phát `.ts` thô; chưa test với nhà cung cấp thật |
| Cast IPTV sang TV | Roadmap | Roadmap | Cần SDK/receiver |
| Quét LAN theo yêu cầu | Implemented | Implemented | /24 unicast; chưa SSDP/mDNS |
| Secure pairing store | Implemented | Implemented | Token Samsung/LG, pin chứng chỉ Google TV, MAC; PSK Sony và đăng nhập Xtream vẫn chỉ trong phiên |

## Cách làm từng bước tiếp theo

1. ✅ 09/10/2026: build Android, 50 test host, compile/link iOS và build Xcode simulator đều qua (xem `docs/context/validation.md`). Còn lại: đi hết flow iOS trên simulator/thiết bị thật.
2. Nghiệm thu Samsung/LG/Sony trên TV thật theo gate GĐ3; nếu LG từ chối manifest không ký thì đổi chiến lược trước khi quảng bá.
3. ✅ 09/10/2026 (code): Google TV Remote v2 (TLS client cert + pairing code), Wake-on-LAN, lưu token bằng Keystore/Keychain — xem `docs/context/remote-discovery-iptv.md`; cần nghiệm thu trên TV thật. Kế tiếp media casting trước full mirroring để giảm rủi ro receiver/codec. Không chạy ads acquisition cho hãng chưa nghiệm thu.
