package sidus-victor.editor;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.TextField.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import sidus-victor.content.*;
import sidus-victor.game.*;
import sidus-victor.gen.*;
import sidus-victor.graphics.*;
import sidus-victor.io.*;
import sidus-victor.type.*;
import sidus-victor.ui.*;
import sidus-victor.ui.dialogs.*;

import java.util.*;

import static sidus-victor.Vars.*;
import static sidus-victor.game.SpawnGroup.*;

public class WaveInfoDialog extends BaseDialog{
    private int displayed = 20;
    Seq<SpawnGroup> groups = new Seq<>();
    private SpawnGroup expandedGroup;

    private Table table;
    private int start = 0;
    private UnitType lastType = UnitTypes.dagger;
    private Sort sort = Sort.begin;
    private boolean reverseSort = false;
    private float updateTimer, updatePeriod = 1f;
    private WaveGraph graph = new WaveGraph();

    public WaveInfoDialog(){
        super("@waves.title");

        shown(this::setup);
        hidden(() -> state.rules.spawns = groups);

        addCloseListener();

        onResize(this::setup);
        buttons.button(Icon.filter, () -> {
            BaseDialog dialog = new BaseDialog("@waves.sort");
            dialog.setFillParent(false);
            dialog.cont.table(Tex.button, t -> {
                for(Sort s : Sort.all){
                    t.button("@waves.sort." + s, Styles.clearTogglet, () -> {
                        sort = s;
                        dialog.hide();
                        buildGroups();
                    }).size(150f, 60f).checked(s == sort);
                }
            }).row();
            dialog.cont.check("@waves.sort.reverse", b -> {
                reverseSort = b;
                buildGroups();
            }).padTop(4).checked(reverseSort).padBottom(8f);
            dialog.addCloseButton();
            dialog.show();
            buildGroups();
        }).size(60f, 64f);

        addCloseButton();

        buttons.button("@waves.edit", Icon.pencil, () -> {
            BaseDialog dialog = new BaseDialog("@waves.edit");
            dialog.addCloseButton();
            dialog.setFillParent(false);
            dialog.cont.table(Tex.button, t -> {
                var style = Styles.cleart;
                t.defaults().size(210f, 58f);

                t.button("@waves.copy", Icon.copy, style, () -> {
                    ui.showInfoFade("@waves.copied");
                    Core.app.setClipboardText(maps.writeWaves(groups));
                    dialog.hide();
                }).disabled(b -> groups == null).marginLeft(12f).row();

                t.button("@waves.load", Icon.download, style, () -> {
                    try{
                        groups = maps.readWaves(Core.app.getClipboardText());
                        buildGroups();
                    }catch(Exception e){
                        e.printStackTrace();
                        ui.showErrorMessage("@waves.invalid");
                    }
                    dialog.hide();
                }).marginLeft(12f).disabled(b -> Core.app.getClipboardText() == null || Core.app.getClipboardText().isEmpty()).row();

                t.button("@settings.reset", Icon.upload, style, () -> ui.showConfirm("@confirm", "@settings.clear.confirm", () -> {
                    groups = JsonIO.copy(waves.get());
                    buildGroups();
                    dialog.hide();
                })).marginLeft(12f).row();

                t.button("@clear", Icon.cancel, style, () -> ui.showConfirm("@confirm", "@settings.clear.confirm", () -> {
                    groups.clear();
                    buildGroups();
                    dialog.hide();
                })).marginLeft(12f);
            });

            dialog.show();
        }).size(250f, 64f);

        buttons.defaults().width(60f);

        buttons.button("<", () -> {}).update(t -> {
            if(t.getClickListener().isPressed()){
                shift(-1);
            }
        });
        buttons.button(">", () -> {}).update(t -> {
            if(t.getClickListener().isPressed()){
                shift(1);
            }
        });

        buttons.button("-", () -> {}).update(t -> {
            if(t.getClickListener().isPressed()){
                view(-1);
            }
        });
        buttons.button("+", () -> {}).update(t -> {
            if(t.getClickListener().isPressed()){
                view(1);
            }
        });

        if(experimental){
            buttons.button("Random", Icon.refresh, () -> {
                groups.clear();
                groups = Waves.generate(1f / 10f);
                updateWaves();
            }).width(200f);
        }
    }

    void view(int amount){
        updateTimer += Time.delta;
        if(updateTimer >= updatePeriod){
            displayed += amount;
            if(displayed < 5) displayed = 5;
            updateTimer = 0f;
            updateWaves();
        }
    }

    void shift(int amount){
        updateTimer += Time.delta;
        if(updateTimer >= updatePeriod){
            start += amount;
            if(start < 0) start = 0;
            updateTimer = 0f;
            updateWaves();
        }
    }

    void setup(){
        groups = JsonIO.copy(state.rules.spawns.isEmpty() ? waves.get() : state.rules.spawns);

        cont.clear();
        cont.stack(new Table(Tex.clear, main -> {
            main.pane(t -> table = t).growX().growY().padRight(8f).scrollX(false);
            main.row();
            main.button("@add", () -> {
                if(groups == null) groups = new Seq<>();
                SpawnGroup newGroup = new SpawnGroup(lastType);
                groups.add(newGroup);
                expandedGroup = newGroup;
                showUpdate(newGroup);
                buildGroups();
            }).growX().height(70f);
        }), new Label("@waves.none"){{
            visible(() -> groups.isEmpty());
            this.touchable = Touchable.disabled;
            setWrap(true);
            setAlignment(Align.center, Align.center);
        }}).width(390f).growY();

        cont.add(graph = new WaveGraph()).grow();

        buildGroups();
    }

    void buildGroups(){
        table.clear();
        table.top();
        table.margin(10f);

        if(groups != null){
            groups.sort(sort.sort);
            if(reverseSort) groups.reverse();

            for(SpawnGroup group : groups){
                table.table(Tex.button, t -> {
                    t.margin(0).defaults().pad(3).padLeft(5f).growX().left();
                    t.button(b -> {
                        b.left();
                        b.image(group.type.uiIcon).size(32f).padRight(3).scaling(Scaling.fit);
                        b.add(group.type.localizedName).color(Pal.accent);

                        b.add().growX();

                        b.label(() -> (group.begin + 1) + "").color(Color.lightGray).minWidth(45f).labelAlign(Align.left).left();

                        b.button(Icon.copySmall, Styles.emptyi, () -> {
                            SpawnGroup newGroup = group.copy();
                            expandedGroup = newGroup;
                            groups.add(newGroup);
                            buildGroups();
                        }).pad(-6).size(46f);

                        b.button(group.effect != null && group.effect != StatusEffects.none ?
                            new TextureRegionDrawable(group.effect.uiIcon) :
                            Icon.logicSmall,
                        Styles.emptyi, () -> showEffect(group)).pad(-6).size(46f);

                        b.button(Icon.unitsSmall, Styles.emptyi, () -> showUpdate(group)).pad(-6).size(46f);
                        b.button(Icon.cancel, Styles.emptyi, () -> {
                            groups.remove(group);
                            table.getCell(t).pad(0f);
                            t.remove();
                            buildGroups();
                        }).pad(-6).size(46f).padRight(-12f);
                    }, () -> {
                        expandedGroup = expandedGroup == group ? null : group;
                        buildGroups();
                    }).height(46f).pad(-6f).padBottom(0f).row();

                    if(expandedGroup == group){
                        t.table(spawns -> {
                            spawns.field("" + (group.begin + 1), TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.begin = Strings.parseInt(text) - 1;
                                    updateWaves();
                                }
                            }).width(100f);
                            spawns.add("@waves.to").padLeft(4).padRight(4);
                            spawns.field(group.end == never ? "" : (group.end + 1) + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.end = Strings.parseInt(text) - 1;
                                    updateWaves();
                                }else if(text.isEmpty()){
                                    group.end = never;
                                    updateWaves();
                                }
                            }).width(100f).get().setMessageText("∞");
                        }).row();

                        t.table(p -> {
                            p.add("@waves.every").padRight(4);
                            p.field(group.spacing + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text) && Strings.parseInt(text) > 0){
                                    group.spacing = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(100f);
                            p.add("@waves.waves").padLeft(4);
                        }).row();

                        t.table(a -> {
                            a.field(group.unitAmount + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.unitAmount = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add(" + ");
                            a.field(Strings.fixed(Math.max((Mathf.zero(group.unitScaling) ? 0 : 1f / group.unitScaling), 0), 2), TextFieldFilter.floatsOnly, text -> {
                                if(Strings.canParsePositiveFloat(text)){
                                    group.unitScaling = 1f / Strings.parseFloat(text);
                                    updateWaves();
                                }
                            }).width(80f);
                            a.add("@waves.perspawn").padLeft(4);
                        }).row();

                        t.table(a -> {
                            a.field(group.max + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.max = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add("@waves.max").padLeft(5);
                        }).row();

                        t.table(a -> {
                            a.field((int)group.shields + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.shields = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add(" + ");
                            a.field((int)group.shieldScaling + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.shieldScaling = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);
                            a.add("@waves.shields").padLeft(4);
                        }).row();

                        t.check("@waves.guardian", b -> {
                            group.effect = (b ? StatusEffects.boss : null);
                            buildGroups();
                        }).padTop(4).update(b -> b.setChecked(group.effect == StatusEffects.boss)).padBottom(8f).row();

                        //spawn positions are clunky and thus experimental for now
                        if(experimental){
                            t.table(a -> {
                                a.add("spawn at ");

                                a.field(group.spawn == -1 ? "" : Point2.x(group.spawn) + "", TextFieldFilter.digitsOnly, text -> {
                                    if(Strings.canParsePositiveInt(text)){
                                        group.spawn = Point2.pack(Strings.parseInt(text), Point2.y(group.spawn));
                                        Log.info(group.spawn);
                                    }
                                }).width(70f);

                                a.add(",");

                                a.field(group.spawn == -1 ? "" : Point2.y(group.spawn) + "", TextFieldFilter.digitsOnly, text -> {
                                    if(Strings.canParsePositiveInt(text)){
                                        group.spawn = Point2.pack(Point2.x(group.spawn), Strings.parseInt(text));
                                        Log.info(group.spawn);
                                    }
                                }).width(70f);
                            }).padBottom(8f).padTop(-8f).row();
                        }
                    }
                }).width(340f).pad(8);

                table.row();
            }
        }else{
            table.add("@editor.default");
        }

        updateWaves();
    }

    void showUpdate(SpawnGroup group){
        BaseDialog dialog = new BaseDialog("");
        dialog.setFillParent(true);
        dialog.cont.pane(p -> {
            int i = 0;
            for(UnitType type : content.units()){
                if(type.isHidden()) continue;
                p.button(t -> {
                    t.left();
                    t.image(type.uiIcon).size(8 * 4).scaling(Scaling.fit).padRight(2f);
                    t.add(type.localizedName);
                }, () -> {
                    lastType = type;
                    group.type = type;
                    dialog.hide();
                    buildGroups();
                }).pad(2).margin(12f).fillX();
                if(++i % 3 == 0) p.row();
            }
        });
        dialog.addCloseButton();
        dialog.show();
    }

    void showEffect(SpawnGroup group){
        BaseDialog dialog = new BaseDialog("");
        dialog.setFillParent(true);
        dialog.cont.pane(p -> {
            int i = 0;
            for(StatusEffect effect : content.statusEffects()){
                if(effect != StatusEffects.none && (effect.isHidden() || effect.reactive)) continue;

                p.button(t -> {
                    t.left();
                    if(effect.uiIcon != null && effect != StatusEffects.none){
                        t.image(effect.uiIcon).size(8 * 4).scaling(Scaling.fit).padRight(2f);
                    }else{
                        t.image(Icon.none).size(8 * 4).scaling(Scaling.fit).padRight(2f);
                    }

                    if(effect != StatusEffects.none){
                        t.add(effect.localizedName);
                    }else{
                        t.add("@settings.resetKey");
                    }
                }, () -> {
                    group.effect = effect;
                    dialog.hide();
                    buildGroups();
                }).pad(2).margin(12f).fillX();
                if(++i % 3 == 0) p.row();
            }
        });
        dialog.addCloseButton();
        dialog.show();
    }

    enum Sort{
        begin(Structs.comps(Structs.comparingFloat(g -> g.begin), Structs.comparingFloat(g -> g.type.id))),
        health(Structs.comps(Structs.comparingFloat(g -> g.type.health), Structs.comparingFloat(g -> g.begin))),
        type(Structs.comps(Structs.comparingFloat(g -> g.type.id), Structs.comparingFloat(g -> g.begin)));

        static final Sort[] all = values();

        final Comparator<SpawnGroup> sort;

        Sort(Comparator<SpawnGroup> sort){
            this.sort = sort;
        }
    }

    void updateWaves(){
        graph.groups = groups;
        graph.from = start;
        graph.to = start + displayed;
        graph.rebuild();
    }
}
