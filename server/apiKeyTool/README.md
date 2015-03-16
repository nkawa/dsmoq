# ApiKeyTool
## 使いかた
### 1. apiKeyTool.jarを生成する
sbtで以下のコマンドを実行する。

	project apiKeyTool
	assembly

\prototype\server\apiKeyTool\target\scala-2.apiKeyTool.jarが配置されている。

### 2. application.confのDB接続情報を書き換える
### 3. コマンドラインから使用する
1. apiKeyTool list
	* 現在登録のあるAPIキーをすべて表示する
2. apiKeyTool search <login name>
	* 指定したユーザーに割り当てたAPIキーをすべて表示する
3. apiKeyTool publish <login name>
	* 指定したユーザーに新規にAPIキーを割り当てる
4. apiKeyTool remove <consumer key>
	* 指定したAPIキーを削除する