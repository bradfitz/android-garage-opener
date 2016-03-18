package main

import (
	"github.com/bradfitz/android-garage-opener/anpher"
)

func main() {
	app := &garageTestApp{}
	anpher.Run(app)
}

type garageTestApp struct {
}

func (a *garageTestApp) HandleEvent(ctx *anpher.Ctx, evt anpher.Event) {
	ctx.Logf("Got event: %s\n", evt) // to logcat

	if le, ok := evt.(*anpher.LifecycleEvent); ok {
		if le.Event == "create" {
			//env.SetOnClick("
		}
	}

	if ce, ok := evt.(*anpher.ClickEvent); ok {
		ctx.Screenf("Clicked! evt=%#v", ce)

		id, ok := ctx.FindViewId("button2")
		if ok && id == ce.Id {
			ctx.Screenf("You clicked bar, which has id %d", id)
		}
	}
}
