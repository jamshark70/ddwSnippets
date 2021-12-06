DDWSnippets {
	classvar <snips, action, <>path,
	<>autoEnable, <>verbose = true,
	<>hotkeyCode = 96, <>hotkeyMods = 262144;

	*new { this.shouldNotImplement(thisMethod) }

	*initClass {
		snips = Dictionary.new;
		Class.initClassTree(Clock);
		if('Document'.asClass.notNil) {
			Class.initClassTree(Document);
		};
		action = { |doc, char, modifiers, unicode, keycode|
			if(keycode == hotkeyCode and: { modifiers bitAnd: hotkeyMods == hotkeyMods }) {
				this.makeGui;  // changes focus to GUI window
			}
		};
		path = Platform.userConfigDir +/+ "ddwSnippets.scd";
		{
			var temp = autoEnable;
			var connected;
			// allows time for user to reset the path in startup.scd
			1.0.wait;
			// sometimes ScIDE doesn't connect quickly enough
			// so I should wait until the link is up
			connected = if('ScIDE'.asClass.notNil) {
				block { |break|
					10.do {
						if(ScIDE.connected) { break.(true) };
						0.2.wait;
					};
					false
				};
			} {
				\noIDE;
			};
			this.read(path, false);
			// user setting of autoEnable in startup.scd should override config
			// temp will be nil if the user didn't set it
			if(temp.notNil) { autoEnable = temp };
			if((autoEnable ?? { true }) and: { 'Document'.asClass.notNil }) {
				if(connected == true) {
					this.enable;
				} {
					if(connected == false) {
						"DDWSnippets is set to autoEnable, but IDE failed to connect".warn;
					};
				};
			};
		}.fork(AppClock);
	}

	*enable {
		var temp = Document.globalKeyDownAction;
		if(temp.isNil or: { not(temp === action or: {
			temp.isKindOf(FunctionList) and: { temp.array.includes(action) }
		}) }) {
			Document.globalKeyDownAction = temp.addFunc(action);
			if(verbose) { "DDWSnippets successfully enabled".postln };
		} {
			if(verbose) { "DDWSnippets already enabled".warn };
		}
	}

	*disable {
		Document.globalKeyDownAction = Document.globalKeyDownAction.removeFunc(action);
	}

	*put { |name, value|
		snips.put(name.asString, value)
	}

	*removeAt { |name|
		snips.removeAt(name.asString)
	}

	*at { |name|
		^snips.at(name)
	}

	*insert { |doc, key|
		var snip = snips[key].value(doc), temp, i, j, pos,
		delete = { |str, i|
			var temp;
			if(i > 0) { temp = str[ .. i-1] } { temp = String.new };
			if(i < (str.size - 2)) { temp = temp ++ str[i+2..] };
			temp
		};
		if(snip.notNil) {
			i = snip.find("##");
			if(i.notNil) {
				snip = delete.(snip, i);
				j = snip.find("##", i+1);
				if(j.notNil) { snip = delete.(snip, j) };
			};
			pos = doc.selectionStart;
			doc.selectedString_(snip);
			if(i.notNil) {
				if(j.notNil) {
					j = j - i;  // reuse j now for selection size
				} {
					j = 0;
				};
				// Yeah. Seriously. I actually have to do this:
				// Document and TextView use different method names
				if(doc.respondsTo(\selectRange)) {
					doc.selectRange(pos + i, j)
				} {
					doc.select(pos + i, j)
				}
			};
			^true
		} {
			"DDWSnippets: % is undefined".format(key).warn;
			^false
		};
	}

	*makeGui { |doc(Document.current)|
		var window, textField, listView, str;
		var allKeys = snips.keys.as(Array).sort, keys, current;
		var update = { |view|
			var i;
			str = view.string;
			current = keys[listView.value ?? { 0 }];
			keys = allKeys.select { |item| item[.. str.size-1] == str };
			i = keys.indexOfEqual(current) ?? { 0 };
			listView.items_(keys).value_(i);
			current = keys[i];
		},
		post = switch(thisProcess.platform.name)  // 'switch' for future requirements
		{ \osx } {
			// no post-action if called from a TextView
			if(doc.isKindOf(Document)) {
				// note: resourceDir is inside the app bundle only in Mac
				// this will not be valid for any other platform!
				{ (Platform.resourceDir +/+ "../..").openOS }
			};
		};

		window = Window("Snippets",
			Rect.aboutPoint(Window.screenBounds.center, 120, 90));
		window.layout = VLayout(
			textField = TextField().fixedHeight_(20),
			listView = ListView()
		);

		textField.action_({ |view|
			this.insert(doc, current);
			window.close;
			if(post.notNil) { post.defer(0.05) };
		})
		.keyDownAction_({ |view, char, modifiers, unicode, keycode|
			var i;
			case
			{ char.ascii == 27 and: { modifiers bitAnd: 0x001E0000 == 0 } } {
				window.close;
				if(post.notNil) { post.defer(0.05) };
			}
			{ #[65362, 65364].includes(keycode) } {
				i = (listView.value + keycode - 65363).clip(0, keys.size - 1);
				if(i != listView.value) {
					listView.value = i;
					current = str = keys[i];
					textField.string = str;
				};
				true
			}
		})
		.keyUpAction_({ |view, char, modifiers, unicode, keycode|
			var i;
			// no ctrl, alt, super
			// 0x001C0000: [262144, 524288, 1048576].reduce('bitOr').asHexString(8)
			case
			{ char.isPrint and: { modifiers bitAnd: 0x001C0000 == 0 } } {
				update.(view);
			}
			{ #[65288, 65535].includes(keycode) } {  // backspace, delete
				update.(view);
			}
		});

		keys = allKeys;
		current = keys[0];
		listView.items_(keys).value_(0)
		.action_({ |view|
			current = keys[view.value];
			textField.string = current;
		})
		.keyDownAction_({ |view, char, modifiers|
			if(char.ascii == 27 and: { modifiers bitAnd: 0x001E0000 == 0 }) {
				window.close;
				if(post.notNil) { post.defer(0.05) };
			};
		})
		.enterKeyAction_({ |view|
			this.insert(doc, current);
			window.close;
			if(post.notNil) { post.defer(0.05) };
		});

		textField.focus(true);
		window.front;
	}

	*write { |filePath(path), fullConfig = true|
		var file = File(filePath, "w");
		if(file.isOpen) {
			protect {
				file << "/*** DDWSnippets config ***/\n\n";
				if(fullConfig) {
					file << "DDWSnippets.autoEnable = " << autoEnable << ";\n";
					file << "DDWSnippets.verbose = " << verbose << ";\n\n";
					file << "DDWSnippets.hotkeyCode = " << hotkeyCode << ";\n";
					file << "DDWSnippets.hotkeyMods = " << hotkeyMods << ";\n\n";
				};
				snips.keysValuesDo { |key, str|
					file << "DDWSnippets.put(" <<< key << ", " <<< str << ");\n\n";
				};
			} { file.close };
		} {
			"DDWSnippets could not write config file to %".format(filePath).warn;
		};
	}

	*read { |filePath(path), warn(true)|
		var file = File(filePath, "r"), str;
		if(file.isOpen) {
			protect {
				str = file.readAllString;
				if(str[..27] == "/*** DDWSnippets config ***/") {
					str.interpret;
				} {
					"DDWSnippets found invalid config file at %".format(filePath).warn;
				};
			} { file.close };
		} {
			if(warn) {
				"DDWSnippets could not open config file at %".format(filePath).warn;
			}
		};
	}

	// GUI to read hotkey from user
	*learn {
		var mods = ["Ctrl-" -> 262144, "Alt-" -> 524288, "Shift-" -> 131072],
		modString = { |modifiers|
			var str = String.new;
			mods.do { |assn|
				if(modifiers bitAnd: assn.value > 0) {
					str = str ++ assn.key;
				};
			};
			str
		},
		modStr, mod, code,
		window, userView,
		cond = Condition.new, rout,
		// how to tell when the user hit the real key?
		// it's different for each supported OS. yup. Qt is "cross platform"
		done = switch(thisProcess.platform.name)
		{ \linux } { { |char, keycode| keycode bitAnd: 0xFF80 != 0xFF80 } }
		{ \osx } { { |char, keycode| (keycode > 62) or: (keycode < 54) } }
		{ \windows } { { |char, keycode| char.ascii > 0 } };

		rout = fork({
			window = Window("Learn DDWSnippets hotkey",
				Rect.aboutPoint(Window.screenBounds.center, 200, 60));
			window.layout = VLayout(
				StaticText().align_(\center)
				.string_("Please type the hotkey combination to trigger snippets"),
				userView = UserView();
			);
			userView.focus(true)
			.keyDownAction_({ |view, char, modifiers, unicode, keycode|
				if(done.value(char, keycode)) {  // platform-specific test, above
					mod = modifiers;
					code = keycode;
					cond.unhang;
				};
				modStr = modString.(modifiers);
				view.refresh;
			})
			.keyUpAction_({ |view, char, modifiers, unicode, keycode|
				modStr = modString.(modifiers);
				view.refresh;
			})
			.drawFunc_({ |view|
				Pen.stringCenteredIn(modStr, view.bounds.moveTo(0, 0),
					color: QPalette.system.color(\windowText));
			});
			window.front;
			cond.hang;  // wait for key

			window.close;
			window = Window("Confirm hotkey",
				Rect.aboutPoint(Window.screenBounds.center, 200, 40));
			window.layout = VLayout(
				StaticText().align_(\center)
				.string_("You pressed " ++ modStr ++ (code bitAnd: 0xFF).asAscii),
				HLayout(
					nil,
					Button().states_([["Apply and Save"]]).action_({
						hotkeyCode = code;
						hotkeyMods = mod;
						this.write;
						window.close;
					}).minWidth_("Apply and Save".bounds.width + 10),
					Button().states_([["Apply"]]).action_({
						hotkeyCode = code;
						hotkeyMods = mod;
						window.close;
					}),
					Button().states_([["Cancel"]]).action_({
						window.close;
					}),
					nil
				)
			);
			window.front;
		}, AppClock);
	}
}
