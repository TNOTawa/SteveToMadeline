// Unreachable desktop/skin integrations for compiling upstream Player.cs unchanged.
// Any unexpected call fails rather than substituting movement behavior.
using System;
using System.Drawing;
using System.Collections.Generic;
namespace DeskMadeline {
    public sealed class PetWindow {
        public static PetWindow Instance => null;
        public Color ResolveHairColor(int count, Color color) => throw new InvalidOperationException();
    }
    public abstract class Glider : IPetHoldable {
        public abstract PointF Pos { get; }
        public abstract PointF Speed { get; }
        public abstract Player Holder { get; }
        public abstract bool IsHeld { get; }
        public abstract bool BeingDragged { get; }
        public abstract bool SlowRun { get; }
        public abstract bool SlowFall { get; }
        public abstract bool CanPickup(Player p);
        public abstract bool Pickup(Player p);
        public abstract void Carry(PointF p);
        public abstract void Release(PointF p, IList<Solid> solids = null);
    }
    public sealed class SmhHairFile { public int? HairFloatingDashCount; public bool HairFlash; }
    public sealed class SmhHair {
        public enum Special { Flash, General, Trail, DashPtcl }
        public SmhHairFile File;
        public bool HairFlashing, HasZeroDashFlash;
        public int LastDashes, LastHairCount;
        public int? GetHairLength(int count) => throw new InvalidOperationException();
        public bool GetHairColorWithSpecified(int type, int dashes, out Color color) => throw new InvalidOperationException();
        public bool SafeGetHairColor(int type, int dashes, int count, out Color color) => throw new InvalidOperationException();
    }
    public sealed class SmhSkin {
        public SmhHair Hair;
        public float IdleAnimationChance;
        public Chooser IdleColdOptions;
        public int PatchModeBadeline(int mode) => throw new InvalidOperationException();
        public int PatchModeNoBackpack(int mode) => throw new InvalidOperationException();
        public int PatchModeIdleOptions(int mode) => throw new InvalidOperationException();
        public void AfterSuperJump(Player p, bool duck) => throw new InvalidOperationException();
        public void AfterSuperWallJump(Player p) => throw new InvalidOperationException();
        public void Play(Player p, string id, bool restart, bool random) => throw new InvalidOperationException();
    }
}
