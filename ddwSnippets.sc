DDWSnippets {
	classvar <snips, action, <>path, <>autoEnable = true, <>verbose = true;

	*new { this.shouldNotImplement(thisMethod) }

	*initClass {
		snips = Dictionary.new;
		Class.initClassTree(Clock);
		Class.initClassTree(Document);
		action = { |doc, char, modifiers, unicode, keycode|
			if(keycode == 96 and: { modifiers bitAnd: 262144 != 0 }) {
				this.makeGui;  // changes focus to GUI window
			}
		};
		path = Platform.userConfigDir +/+ "ddwSnippets.scd";
		AppClock.sched(1.0, {
			// allows time for user to reset the path in startup.scd
			this.read(path, false);
			if(autoEnable) {
				this.enable;
			};
		});
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
		snips.put(name.asString, value.asString)
	}

	*removeAt { |name|
		snips.removeAt(name.asString)
	}

	*at { |name|
		^snips.at(name.asString)
	}

	*insert { |doc, key|
		var snip = snips[key], temp, i, j, pos,
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
		})
		.keyDownAction_({ |view, char, modifiers, unicode, keycode|
			var i;
			case
			{ char.ascii == 27 and: { modifiers bitAnd: 0x001E0000 == 0 } } {
				window.close;
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
			};
		})
		.enterKeyAction_({ |view|
			this.insert(doc, current);
			window.close;
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
}
