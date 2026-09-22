import 'package:flutter_test/flutter_test.dart';
import 'package:wechat_video_call_example/main.dart';

void main() {
  testWidgets('demo home page builds', (tester) async {
    await tester.pumpWidget(const WvcExampleApp());
    expect(find.text('wechat_video_call Demo'), findsOneWidget);
    expect(find.text('坐标向导（插件小窗）'), findsOneWidget);
  });
}
