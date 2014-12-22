# AdminTool
## 使いかた
### 1. adminTool.jarを生成する
sbtで以下のコマンドを実行する。

	project adminTool
	assembly

\prototype\server\adminTool\target\scala-2.11以下にadminTool.jarが配置されている。

### 2. application.confのDB接続情報を書き換える
### 3. コマンドラインから使用する
1. adminTool list
	* 現在登録のあるAPIキーをすべて表示する
2. adminTool search <login name>
	* 指定したユーザーに割り当てたAPIキーをすべて表示する
3. adminTool publish <login name>
	* 指定したユーザーに新規にAPIキーを割り当てる
4. adminTool remove <consumer key>
	* 指定したAPIキーを削除する